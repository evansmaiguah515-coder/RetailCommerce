
import base64
import os
import re
from datetime import datetime
from pathlib import Path

import firebase_admin
import requests
from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException
from firebase_admin import auth, credentials, firestore
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent
load_dotenv(BASE_DIR / ".env", encoding="utf-8-sig")

MPESA_ENV = os.getenv("MPESA_ENV", "sandbox").strip().lower()
CONSUMER_KEY = os.getenv("MPESA_CONSUMER_KEY", "").strip()
CONSUMER_SECRET = os.getenv("MPESA_CONSUMER_SECRET", "").strip()
SHORTCODE = os.getenv("MPESA_SHORTCODE", "").strip()
PASSKEY = os.getenv("MPESA_PASSKEY", "").strip()
CALLBACK_URL = os.getenv("MPESA_CALLBACK_URL", "").strip()
CALLBACK_SECRET = os.getenv("MPESA_CALLBACK_SECRET", "").strip()

if MPESA_ENV not in {"sandbox", "production"}:
    raise RuntimeError("MPESA_ENV must be sandbox or production")

MPESA_BASE_URL = (
    "https://sandbox.safaricom.co.ke"
    if MPESA_ENV == "sandbox"
    else "https://api.safaricom.co.ke"
)

service_account = os.getenv(
    "FIREBASE_SERVICE_ACCOUNT", "service-account.json"
)
service_account_path = BASE_DIR / service_account

if not service_account_path.is_file():
    raise RuntimeError(
        f"Firebase service account not found: {service_account_path}"
    )

if not firebase_admin._apps:
    firebase_admin.initialize_app(
        credentials.Certificate(str(service_account_path))
    )

db = firestore.client()
app = FastAPI(title="RetailCommerce M-Pesa Backend")


class STKPushRequest(BaseModel):
    orderId: str = Field(min_length=1, max_length=150)
    phoneNumber: str = Field(min_length=9, max_length=15)


def require_firebase_user(authorization: str | None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=401, detail="Missing Firebase sign-in token"
        )

    try:
        decoded = auth.verify_id_token(
            authorization.removeprefix("Bearer ").strip()
        )
        return decoded["uid"]
    except Exception:
        raise HTTPException(
            status_code=401, detail="Invalid or expired sign-in token"
        )


def normalize_phone(value: str) -> str:
    phone = re.sub(r"[\s()+-]", "", value)

    if phone.startswith("0") and len(phone) == 10:
        phone = "254" + phone[1:]
    elif phone.startswith(("7", "1")) and len(phone) == 9:
        phone = "254" + phone
    elif phone.startswith("+254"):
        phone = phone[1:]

    if not re.fullmatch(r"254[17]\d{8}", phone):
        raise HTTPException(
            status_code=400,
            detail="Enter a valid Kenyan M-Pesa phone number",
        )

    return phone


def get_daraja_access_token() -> str:
    if not CONSUMER_KEY or not CONSUMER_SECRET:
        raise HTTPException(
            status_code=503, detail="Daraja credentials are not configured"
        )

    response = requests.get(
        f"{MPESA_BASE_URL}/oauth/v1/generate",
        params={"grant_type": "client_credentials"},
        auth=(CONSUMER_KEY, CONSUMER_SECRET),
        timeout=30,
    )

    if not response.ok:
        raise HTTPException(
            status_code=502, detail="Could not authenticate with Daraja"
        )

    token = response.json().get("access_token")
    if not token:
        raise HTTPException(
            status_code=502, detail="Daraja returned no access token"
        )

    return token


@app.get("/health")
def health_check():
    return {
        "status": "ok",
        "service": "RetailCommerce M-Pesa Backend",
        "environment": MPESA_ENV,
    }


@app.post("/mpesa/stkpush")
def initiate_stk_push(
    request: STKPushRequest,
    authorization: str | None = Header(default=None),
):
    uid = require_firebase_user(authorization)

    if not all([CONSUMER_KEY, CONSUMER_SECRET, SHORTCODE, PASSKEY]):
        raise HTTPException(
            status_code=503,
            detail="Daraja credentials are not fully configured",
        )

    if (
        not CALLBACK_URL.startswith("https://")
        or "replace-with-your-public-callback-url" in CALLBACK_URL
        or not CALLBACK_SECRET
        or "replace_with_" in CALLBACK_SECRET
    ):
        raise HTTPException(
            status_code=503,
            detail="Configure the public HTTPS callback URL and callback secret first",
        )

    order_ref = db.collection("orders").document(request.orderId)
    snapshot = order_ref.get()

    if not snapshot.exists:
        raise HTTPException(status_code=404, detail="Order not found")

    order = snapshot.to_dict() or {}

    if order.get("userId") != uid:
        raise HTTPException(status_code=403, detail="Order does not belong to you")

    if order.get("status") != "pending":
        raise HTTPException(
            status_code=409, detail="Order is not awaiting payment"
        )

    try:
        amount = int(float(order.get("totalAmount", 0)))
    except (ValueError, TypeError):
        amount = 0

    if amount < 1:
        raise HTTPException(status_code=400, detail="Invalid order amount")

    phone = normalize_phone(request.phoneNumber)
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
    password = base64.b64encode(
        f"{SHORTCODE}{PASSKEY}{timestamp}".encode()
    ).decode()

    try:
        token = get_daraja_access_token()
        response = requests.post(
            f"{MPESA_BASE_URL}/mpesa/stkpush/v1/processrequest",
            headers={"Authorization": f"Bearer {token}"},
            json={
                "BusinessShortCode": SHORTCODE,
                "Password": password,
                "Timestamp": timestamp,
                "TransactionType": "CustomerPayBillOnline",
                "Amount": amount,
                "PartyA": phone,
                "PartyB": SHORTCODE,
                "PhoneNumber": phone,
                "CallBackURL": CALLBACK_URL,
                "AccountReference": f"Order{request.orderId}"[:12],
                "TransactionDesc": "RetailCommerce order payment",
            },
            timeout=40,
        )
        result = response.json()
    except (requests.RequestException, ValueError):
        raise HTTPException(
            status_code=502,
            detail="Could not obtain a valid response from Daraja",
        )

    if not response.ok or result.get("ResponseCode") != "0":
        raise HTTPException(
            status_code=502,
            detail={
                "message": "Daraja did not accept the STK Push request",
                "daraja": result,
            },
        )

    checkout_id = result.get("CheckoutRequestID")
    if not checkout_id:
        raise HTTPException(
            status_code=502, detail="Daraja returned no checkout request ID"
        )

    order_ref.update({
        "paymentMethod": "mpesa",
        "paymentStatus": "processing",
        "checkoutRequestId": checkout_id,
        "mpesaPhoneNumber": phone,
    })

    return {
        "message": "STK Push request accepted. Await the payment result.",
        "checkoutRequestId": checkout_id,
        "customerMessage": result.get("CustomerMessage", ""),
    }


@app.post("/mpesa/callback/{callback_secret}")
def mpesa_callback(callback_secret: str, payload: dict):
    if not CALLBACK_SECRET or callback_secret != CALLBACK_SECRET:
        raise HTTPException(status_code=404, detail="Not found")

    callback = payload.get("Body", {}).get("stkCallback", {})
    checkout_id = callback.get("CheckoutRequestID")
    result_code = callback.get("ResultCode")

    if not checkout_id or result_code is None:
        raise HTTPException(status_code=400, detail="Invalid callback payload")

    matches = (
        db.collection("orders")
        .where("checkoutRequestId", "==", checkout_id)
        .limit(1)
        .get()
    )
    order_snapshot = next(iter(matches), None)

    if order_snapshot is None:
        raise HTTPException(status_code=404, detail="Matching order not found")

    order_ref = order_snapshot.reference
    order = order_snapshot.to_dict() or {}

    if order.get("paymentStatus") == "paid":
        return {"ResultCode": 0, "ResultDesc": "Already processed"}

    if int(result_code) == 0:
        items = callback.get("CallbackMetadata", {}).get("Item", [])
        values = {item.get("Name"): item.get("Value") for item in items}

        order_ref.update({
            "status": "paid",
            "paymentStatus": "paid",
            "mpesaReceiptNumber": values.get("MpesaReceiptNumber"),
            "mpesaAmount": values.get("Amount"),
            "mpesaTransactionDate": values.get("TransactionDate"),
            "mpesaPaidPhone": values.get("PhoneNumber"),
        })
    else:
        order_ref.update({
            "paymentStatus": "failed",
            "mpesaResultCode": int(result_code),
            "mpesaResultDescription": callback.get("ResultDesc", ""),
        })

    return {"ResultCode": 0, "ResultDesc": "Callback processed"}
