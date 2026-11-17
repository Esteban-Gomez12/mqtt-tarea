package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.firebase.database.FirebaseDatabase

data class Usuario(
    val id: String = "",
    val nombre: String = "",
    val apellido: String = "",
    val edad: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FormularioFirebase()
                }
            }
        }
    }
}

@Composable
fun FormularioFirebase() {
    var nombre by remember { mutableStateOf("") }
    var apellido by remember { mutableStateOf("") }
    var edad by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Referencia a Firebase Realtime Database
    val database = FirebaseDatabase.getInstance("https://fir-tarea-201c3-default-rtdb.firebaseio.com").reference

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Registro en Firebase",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = apellido,
            onValueChange = { apellido = it },
            label = { Text("Apellido") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = edad,
            onValueChange = { edad = it },
            label = { Text("Edad") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (nombre.isBlank() || apellido.isBlank() || edad.isBlank()) {
                    Toast.makeText(context, "Complete todos los campos", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val edadInt = edad.toIntOrNull() ?: 0
                val userId = database.child("users").push().key

                if (userId != null) {
                    val usuario = Usuario(userId, nombre.trim(), apellido.trim(), edadInt)

                    // Escritura en nodo users/{userId}
                    database.child("users").child(userId).setValue(usuario)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Guardado en Firebase", Toast.LENGTH_SHORT).show()
                            nombre = ""
                            apellido = ""
                            edad = ""
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }

                    // Actualización simultánea con updateChildren() (equivalente a update() en pauta)
                    val updates = hashMapOf<String, Any>(
                        "/users/$userId" to usuario,
                        "/registro-historico/$userId" to usuario
                    )
                    database.updateChildren(updates)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar en Firebase")
        }
    }
}