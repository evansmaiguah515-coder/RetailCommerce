
package com.example.retailcommerce

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

private const val MPESA_BACKEND_URL =
    "https://lifter-juggle-issuing.ngrok-free.dev"

private val Dark = Color(0xFF111310)
private val Panel = Color(0xFF1C1F1A)
private val Lime = Color(0xFFC5F467)
private val Muted = Color(0xFF9DA397)
private val White = Color(0xFFF5F6F0)

data class Product(
    val id: Int,
    val name: String,
    val model: String,
    val price: Int,
    val category: String,
    val stock: Int,
    val emoji: String,
    val tag: String
)

data class OrderHistoryItem(
    val id: String,
    val totalAmount: Long,
    val status: String,
    val createdAt: Timestamp?,
    val items: List<Map<String, Any?>>
)

private val products = listOf(
    Product(1, "Wireless Headphones", "Clear sound • Comfortable fit", 2500, "Electronics", 12, "🎧", "POPULAR"),
    Product(2, "Smart Watch", "Activity tracking • Daily use", 3200, "Electronics", 8, "⌚", "TRENDING"),
    Product(3, "Travel Backpack", "Everyday carry • Spacious design", 1800, "Fashion", 15, "🎒", "BESTSELLER"),
    Product(4, "Desk Lamp", "Modern lighting • Study and work", 1200, "Home", 10, "💡", "NEW"),
    Product(5, "Water Bottle", "Reusable • Everyday hydration", 650, "Home", 20, "🧴", "VALUE"),
    Product(6, "Running Shoes", "Comfortable • Active lifestyle", 2800, "Fashion", 7, "👟", "POPULAR"),
    Product(7, "Skincare Set", "Daily skincare • Personal care", 1500, "Beauty", 9, "✨", "NEW ARRIVAL"),
    Product(8, "Bluetooth Speaker", "Portable • Wireless audio", 2100, "Electronics", 11, "🔊", "TRENDING")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Lime,
                    onPrimary = Dark,
                    background = Dark,
                    surface = Panel,
                    onSurface = White
                )
            ) {
                RetailCommerceApp()
            }
        }
    }
}

@Composable
fun RetailStore() {
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var cart by remember { mutableStateOf(mapOf<Int, Int>()) }
    var message by remember { mutableStateOf<String?>(null) }

    var showCartDialog by remember { mutableStateOf(false) }
    var isSavingOrder by remember { mutableStateOf(false) }

    var showPhoneDialog by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var isInitiatingPayment by remember { mutableStateOf(false) }
    var pendingOrderId by remember { mutableStateOf<String?>(null) }

    var showOrderHistory by remember { mutableStateOf(false) }
    var isLoadingOrders by remember { mutableStateOf(false) }
    var orderHistory by remember {
        mutableStateOf<List<OrderHistoryItem>>(emptyList())
    }

    // NEW: Account and sign-out dialog state.
    var showAccountDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    val categories = listOf("All", "Electronics", "Home", "Fashion", "Beauty")

    val visibleProducts = products.filter { product ->
        val matchesSearch =
            product.name.contains(search, ignoreCase = true) ||
                    product.model.contains(search, ignoreCase = true) ||
                    product.category.contains(search, ignoreCase = true)

        val matchesCategory =
            category == "All" || product.category == category

        matchesSearch && matchesCategory
    }

    val cartCount = cart.values.sum()

    val cartTotal = cart.entries.sumOf { entry ->
        (products.find { it.id == entry.key }?.price ?: 0) * entry.value
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Dark
    ) {
        Column {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 18.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Lime
                            ) {
                                Text(
                                    "RC",
                                    modifier = Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 8.dp
                                    ),
                                    color = Dark,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }

                            Column {
                                Text(
                                    "RETAIL COMMERCE",
                                    color = White,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.4.sp,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "EVERYDAY ESSENTIALS",
                                    color = Muted,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Existing Orders button.
                            TextButton(
                                contentPadding = PaddingValues(
                                    horizontal = 6.dp,
                                    vertical = 8.dp
                                ),
                                onClick = {
                                    val user =
                                        FirebaseAuth.getInstance().currentUser

                                    if (user == null) {
                                        message =
                                            "Please sign in to view your orders."
                                    } else {
                                        showOrderHistory = true
                                        isLoadingOrders = true

                                        FirebaseFirestore.getInstance()
                                            .collection("orders")
                                            .whereEqualTo("userId", user.uid)
                                            .get()
                                            .addOnSuccessListener { result ->
                                                orderHistory =
                                                    result.documents.map { document ->
                                                        val rawItems =
                                                            document.get("items")
                                                                    as? List<*>

                                                        val orderItems =
                                                            rawItems?.mapNotNull { item ->
                                                                @Suppress("UNCHECKED_CAST")
                                                                item as? Map<String, Any?>
                                                            } ?: emptyList()

                                                        OrderHistoryItem(
                                                            id = document.id,
                                                            totalAmount =
                                                                document.getLong(
                                                                    "totalAmount"
                                                                ) ?: 0L,
                                                            status =
                                                                document.getString(
                                                                    "status"
                                                                ) ?: "unknown",
                                                            createdAt =
                                                                document.getTimestamp(
                                                                    "createdAt"
                                                                ),
                                                            items = orderItems
                                                        )
                                                    }.sortedByDescending {
                                                        it.createdAt
                                                            ?.toDate()
                                                            ?.time ?: 0L
                                                    }

                                                isLoadingOrders = false
                                            }
                                            .addOnFailureListener {
                                                isLoadingOrders = false
                                                message =
                                                    "Could not load your orders. Please try again."
                                            }
                                    }
                                }
                            ) {
                                Text(
                                    "Orders",
                                    color = Lime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // NEW: Account button.
                            TextButton(
                                contentPadding = PaddingValues(
                                    horizontal = 6.dp,
                                    vertical = 8.dp
                                ),
                                onClick = {
                                    showAccountDialog = true
                                }
                            ) {
                                Text(
                                    "Account",
                                    color = Lime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Existing cart indicator.
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Panel
                            ) {
                                Text(
                                    "🛒 $cartCount",
                                    modifier = Modifier.padding(10.dp),
                                    color = Lime,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "YOUR EVERYDAY STORE.",
                            color = Lime,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Find your\nnext favourite.",
                            color = White,
                            fontSize = 38.sp,
                            lineHeight = 43.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Useful finds for your home, lifestyle, and everyday needs.",
                            color = Muted,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF343D27),
                                        Color(0xFF20271B),
                                        Panel
                                    )
                                ),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "THE EVERYDAY EDIT",
                            color = Lime,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Good finds.\nBetter living.",
                                    color = White,
                                    fontSize = 27.sp,
                                    lineHeight = 31.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Explore products for every day.",
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                                Button(
                                    onClick = { category = "All" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Lime,
                                        contentColor = Dark
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "Explore products  →",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text("🛍️", fontSize = 76.sp)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = {
                            Text("Search products...", color = Muted)
                        },
                        leadingIcon = {
                            Text("⌕", color = Lime, fontSize = 26.sp)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Lime,
                            unfocusedBorderColor = Color(0xFF393D35),
                            cursorColor = Lime,
                            focusedContainerColor = Panel,
                            unfocusedContainerColor = Panel
                        )
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "SHOP BY CATEGORY",
                            color = White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            categories.forEach { itemCategory ->
                                val active = category == itemCategory

                                Surface(
                                    onClick = { category = itemCategory },
                                    shape = RoundedCornerShape(50.dp),
                                    color = if (active) Lime else Panel
                                ) {
                                    Text(
                                        itemCategory,
                                        modifier = Modifier.padding(
                                            horizontal = 17.dp,
                                            vertical = 11.dp
                                        ),
                                        color = if (active) Dark else White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Featured products",
                            color = White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${visibleProducts.size} items",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                }

                if (visibleProducts.isEmpty()) {
                    item {
                        Text(
                            "No products found. Try another search.",
                            color = Muted
                        )
                    }
                }

                items(visibleProducts, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        quantity = cart[product.id] ?: 0,
                        onAdd = {
                            val current = cart[product.id] ?: 0

                            if (current < product.stock) {
                                cart = cart + (product.id to current + 1)
                                message = "${product.name} added to your cart."
                            } else {
                                message =
                                    "You have reached the available sample stock."
                            }
                        }
                    )
                }
            }

            Surface(
                color = Color(0xFF191C17),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "CART TOTAL",
                            color = Muted,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            "KES ${"%,d".format(cartTotal)}",
                            color = White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (cartCount == 0) {
                                message =
                                    "Your cart is empty. Add a product first."
                            } else {
                                showCartDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Lime,
                            contentColor = Dark
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("View cart  →", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // NEW: Account details dialog.
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = {
                showAccountDialog = false
            },
            containerColor = Panel,
            titleContentColor = White,
            textContentColor = White,
            title = {
                Text("My account")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Signed in as",
                        color = Muted,
                        fontSize = 12.sp
                    )

                    Text(
                        FirebaseAuth.getInstance().currentUser?.email
                            ?: "Email unavailable",
                        color = White,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "Your orders and checkout are linked to your account.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccountDialog = false
                        showSignOutConfirmation = true
                    }
                ) {
                    Text(
                        "Sign out",
                        color = Color(0xFFFF7777),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAccountDialog = false
                    }
                ) {
                    Text("Close", color = Lime)
                }
            }
        )
    }

    // NEW: Ask for confirmation before signing out.
    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showSignOutConfirmation = false
            },
            containerColor = Panel,
            titleContentColor = White,
            textContentColor = White,
            title = {
                Text("Sign out?")
            },
            text = {
                Text(
                    "Are you sure you want to sign out of Retail Commerce?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirmation = false
                        FirebaseAuth.getInstance().signOut()
                    }
                ) {
                    Text(
                        "Yes, sign out",
                        color = Color(0xFFFF7777),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirmation = false
                    }
                ) {
                    Text("Cancel", color = Lime)
                }
            }
        )
    }

    // General information and error messages.
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            containerColor = Panel,
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Retail Commerce") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text("OK", color = Lime)
                }
            }
        )
    }

    // Order history.
    if (showOrderHistory) {
        AlertDialog(
            onDismissRequest = { showOrderHistory = false },
            containerColor = Panel,
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Your orders") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when {
                        isLoadingOrders -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Lime)
                            }
                        }

                        orderHistory.isEmpty() -> {
                            Text(
                                "You haven't placed any orders yet.",
                                color = Muted
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(orderHistory, key = { it.id }) { order ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = Dark
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            Text(
                                                "Order #${order.id.takeLast(8)}",
                                                color = White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "KES ${"%,d".format(order.totalAmount)}",
                                                color = Lime,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Status: ${order.status.uppercase()}",
                                                color = if (
                                                    order.status.lowercase() in
                                                    listOf("paid", "delivered")
                                                ) Lime else Muted
                                            )
                                            Text(
                                                order.createdAt?.toDate()?.let {
                                                    SimpleDateFormat(
                                                        "dd MMM yyyy, HH:mm",
                                                        Locale.getDefault()
                                                    ).format(it)
                                                } ?: "Date unavailable",
                                                color = Muted,
                                                fontSize = 12.sp
                                            )
                                            HorizontalDivider(color = Panel)

                                            order.items.forEach { item ->
                                                val name =
                                                    item["name"]?.toString() ?: "Product"
                                                val quantity =
                                                    (item["quantity"] as? Number)
                                                        ?.toInt() ?: 1
                                                val subtotal =
                                                    (item["subtotal"] as? Number)
                                                        ?.toLong()

                                                Text(
                                                    "$name × $quantity",
                                                    color = White,
                                                    fontSize = 13.sp
                                                )
                                                if (subtotal != null) {
                                                    Text(
                                                        "KES ${"%,d".format(subtotal)}",
                                                        color = Muted,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOrderHistory = false }) {
                    Text("Close", color = Lime)
                }
            }
        )
    }

    // Cart review and order creation.
    if (showCartDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSavingOrder) showCartDialog = false
            },
            containerColor = Panel,
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Your order") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cart.forEach { (productId, quantity) ->
                        val product = products.find { it.id == productId }
                        if (product != null) {
                            Text("${product.name} × $quantity", color = White)
                            Text(
                                "KES ${"%,d".format(product.price * quantity)}",
                                color = Lime
                            )
                        }
                    }

                    HorizontalDivider(color = Muted)

                    Text(
                        "Total: KES ${"%,d".format(cartTotal)}",
                        color = Lime,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Payment method: M-Pesa",
                        color = Muted
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSavingOrder && cartCount > 0,
                    onClick = {
                        val user = FirebaseAuth.getInstance().currentUser

                        if (user == null) {
                            showCartDialog = false
                            message = "Please sign in before placing an order."
                            return@Button
                        }

                        val orderItems = cart.mapNotNull { (productId, quantity) ->
                            val product = products.find { it.id == productId }
                                ?: return@mapNotNull null

                            mapOf(
                                "productId" to product.id,
                                "name" to product.name,
                                "unitPrice" to product.price,
                                "quantity" to quantity,
                                "subtotal" to product.price * quantity
                            )
                        }

                        if (orderItems.isEmpty()) {
                            message = "Your cart is empty."
                            return@Button
                        }

                        val orderData = hashMapOf<String, Any>(
                            "userId" to user.uid,
                            "items" to orderItems,
                            "totalAmount" to cartTotal,
                            "currency" to "KES",
                            "status" to "pending",
                            "paymentStatus" to "pending",
                            "createdAt" to FieldValue.serverTimestamp()
                        )

                        isSavingOrder = true

                        FirebaseFirestore.getInstance()
                            .collection("orders")
                            .add(orderData)
                            .addOnSuccessListener { document ->
                                isSavingOrder = false
                                showCartDialog = false
                                pendingOrderId = document.id
                                phoneNumber = ""
                                showPhoneDialog = true
                            }
                            .addOnFailureListener { error ->
                                isSavingOrder = false
                                message =
                                    "Could not save your order: " +
                                            (error.localizedMessage ?: "Please try again.")
                            }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Lime,
                        contentColor = Dark
                    )
                ) {
                    Text(if (isSavingOrder) "Saving..." else "Continue to M-Pesa")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSavingOrder,
                    onClick = { showCartDialog = false }
                ) {
                    Text("Cancel", color = Lime)
                }
            }
        )
    }

    // M-Pesa phone number and STK Push.
    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isInitiatingPayment) showPhoneDialog = false
            },
            containerColor = Panel,
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Pay with M-Pesa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Order total: KES ${"%,d".format(cartTotal)}",
                        color = Lime,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Enter the Kenyan M-Pesa number that should receive the payment prompt.",
                        color = Muted
                    )
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isInitiatingPayment,
                        label = { Text("M-Pesa phone number") },
                        placeholder = { Text("07XXXXXXXX") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Lime,
                            unfocusedBorderColor = Muted,
                            focusedLabelColor = Lime,
                            cursorColor = Lime
                        )
                    )
                    Text(
                        "You will need to enter your M-Pesa PIN on your phone to authorize payment.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isInitiatingPayment &&
                            phoneNumber.isNotBlank() &&
                            pendingOrderId != null,
                    onClick = {
                        val user = FirebaseAuth.getInstance().currentUser
                        val orderId = pendingOrderId

                        if (user == null || orderId == null) {
                            message = "Your session or order is missing. Please sign in again."
                            showPhoneDialog = false
                            return@Button
                        }

                        isInitiatingPayment = true

                        // Firebase provides a short-lived ID token.
                        // Daraja secrets stay on the backend.
                        user.getIdToken(false)
                            .addOnSuccessListener { tokenResult ->
                                val idToken = tokenResult.token

                                if (idToken.isNullOrBlank()) {
                                    isInitiatingPayment = false
                                    message = "Could not verify your sign-in. Please try again."
                                    return@addOnSuccessListener
                                }

                                val enteredPhone = phoneNumber.trim()

                                thread {
                                    var success = false
                                    var resultMessage =
                                        "Could not contact the payment server."

                                    var connection: HttpURLConnection? = null

                                    try {
                                        connection = (
                                                URL("$MPESA_BACKEND_URL/mpesa/stkpush")
                                                    .openConnection() as HttpURLConnection
                                                ).apply {
                                                requestMethod = "POST"
                                                connectTimeout = 20000
                                                readTimeout = 45000
                                                doOutput = true
                                                setRequestProperty(
                                                    "Content-Type",
                                                    "application/json"
                                                )
                                                setRequestProperty(
                                                    "Authorization",
                                                    "Bearer $idToken"
                                                )
                                            }

                                        val body = JSONObject().apply {
                                            put("orderId", orderId)
                                            put("phoneNumber", enteredPhone)
                                        }.toString()

                                        connection.outputStream.use { output ->
                                            output.write(body.toByteArray(Charsets.UTF_8))
                                        }

                                        val statusCode = connection.responseCode
                                        val responseStream =
                                            if (statusCode in 200..299) {
                                                connection.inputStream
                                            } else {
                                                connection.errorStream
                                            }

                                        val responseText = responseStream
                                            ?.bufferedReader()
                                            ?.use { it.readText() }
                                            .orEmpty()

                                        val responseJson = try {
                                            JSONObject(responseText)
                                        } catch (_: Exception) {
                                            JSONObject()
                                        }

                                        if (statusCode in 200..299) {
                                            success = true
                                            resultMessage =
                                                responseJson.optString(
                                                    "customerMessage",
                                                    "STK Push request accepted. Check your phone and follow the M-Pesa prompt."
                                                ).ifBlank {
                                                    "STK Push request accepted. Check your phone."
                                                }
                                        } else {
                                            val detail = responseJson.opt("detail")
                                            resultMessage = when (detail) {
                                                is String -> detail
                                                is JSONObject ->
                                                    detail.optString(
                                                        "message",
                                                        "Payment request failed (HTTP $statusCode)."
                                                    )
                                                else ->
                                                    "Payment request failed (HTTP $statusCode)."
                                            }
                                        }
                                    } catch (error: Exception) {
                                        resultMessage =
                                            "Could not reach the payment server. " +
                                                    (error.localizedMessage ?: "Check your connection.")
                                    } finally {
                                        connection?.disconnect()
                                    }

                                    Handler(Looper.getMainLooper()).post {
                                        isInitiatingPayment = false

                                        if (success) {
                                            showPhoneDialog = false
                                            cart = emptyMap()
                                            pendingOrderId = null
                                            message =
                                                "$resultMessage\n\nYour order will only be marked paid after the backend processes the M-Pesa callback."
                                        } else {
                                            message =
                                                "$resultMessage\n\nYour order remains pending. You can correct the number and try again."
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener { error ->
                                isInitiatingPayment = false
                                message =
                                    "Could not verify your sign-in: " +
                                            (error.localizedMessage ?: "Please try again.")
                            }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Lime,
                        contentColor = Dark
                    )
                ) {
                    if (isInitiatingPayment) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Dark,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Requesting...")
                    } else {
                        Text("Send STK Push")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isInitiatingPayment,
                    onClick = { showPhoneDialog = false }
                ) {
                    Text("Later", color = Lime)
                }
            }
        )
    }
}

@Composable
fun ProductCard(
    product: Product,
    quantity: Int,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF30392A),
                                Color(0xFF22251F),
                                Color(0xFF171A16)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("▯", color = Color(0xFF626C54), fontSize = 110.sp)
                Text(product.emoji, fontSize = 57.sp)

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(7.dp),
                    color = Lime
                ) {
                    Text(
                        product.tag,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        ),
                        color = Dark,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF111310)
                ) {
                    Text(
                        product.category,
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        ),
                        color = White,
                        fontSize = 10.sp
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    product.name,
                    color = White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(product.model, color = Muted, fontSize = 12.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "PRICE",
                            color = Muted,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "KES ${"%,d".format(product.price)}",
                            color = Lime,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (product.stock > 0) {
                                "${product.stock} available"
                            } else {
                                "Out of stock"
                            },
                            color = if (product.stock > 0) Lime else Color(0xFFFF7777),
                            fontSize = 10.sp
                        )

                        Spacer(Modifier.height(5.dp))

                        Button(
                            onClick = onAdd,
                            enabled = product.stock > quantity,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Lime,
                                contentColor = Dark,
                                disabledContainerColor = Color(0xFF454940),
                                disabledContentColor = Muted
                            ),
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = PaddingValues(
                                horizontal = 14.dp,
                                vertical = 4.dp
                            )
                        ) {
                            Text(
                                if (quantity > 0) "Added ($quantity)" else "＋ Add",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
