package com.example.mar17

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay


class SmartLearning : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(5000)
                    showSplash = false
                }

                if (showSplash == true) {
                    SplashScreen()
                } else {
                    Scaffold(
                        topBar = { TopBar() },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        MyScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Blue),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "Smart Learning",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Loading...",
                fontSize = 16.sp,
                color = Color.White
            )

            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun TopBar() {
    TopAppBar(
        title = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Smart Learning",
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        },
        navigationIcon = {

            Icon(Icons.Default.Menu,"Menu", tint = Color.White)

        },
        actions = {
            IconButton(onClick = {}) {
                Icon(Icons.Default.MoreVert, "More", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Blue
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun MyScreen(modifier: Modifier = Modifier) {

    var rating by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("Select Category") }

    val categories = listOf("CSE", "IT", "MBA")

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        item {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = {
                    expanded = !expanded
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selected,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                selected = category
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(400.dp),
                // Added spacing so the blue squares don't bleed into each other
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(Color.Blue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Box1", color = Color.White)
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(Color.Blue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Box2", color = Color.White)
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(Color.Blue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Box3", color = Color.White)
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(Color.Blue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Box4", color = Color.White)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                for(i in 1..5) {
                    val starColor = if (i <= rating) {
                        Color.Yellow
                    } else {
                        Color.LightGray
                    }

                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rate $i stars",
                        tint = starColor,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { rating = i }
                            .padding(4.dp)
                    )
                }
            }
        }

    }
}
