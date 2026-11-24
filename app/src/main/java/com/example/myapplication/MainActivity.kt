package com.example.myapplication

import android.os.Bundle
import android.util.Log
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
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import java.nio.charset.StandardCharsets
import java.util.UUID

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

    // Estados para MQTT
    var estadoMqtt by remember { mutableStateOf("MQTT: Conectando...") }
    var mensajeRecibidoMqtt by remember { mutableStateOf("Sin mensajes recibidos aún") }

    val context = LocalContext.current
    val topico = "st/android/prueba"

    // Referencia a Firebase Realtime Database
    val database = FirebaseDatabase.getInstance("https://fir-tarea-201c3-default-rtdb.firebaseio.com").reference

    // Cliente MQTT persistente en el ciclo de vida del Composable
    val mqttClient: Mqtt3AsyncClient = remember {
        MqttClient.builder()
            .useMqttVersion3()
            .identifier("android-${UUID.randomUUID()}")
            .serverHost("broker.emqx.io")
            .serverPort(1883)
            .buildAsync()
    }

    // Conexión, suscripción y limpieza automática al cerrar pantalla
    DisposableEffect(Unit) {
        mqttClient.connect().whenComplete { _, throwable ->
            if (throwable != null) {
                estadoMqtt = "MQTT Error: ${throwable.message}"
                Log.e("MQTT", "Error al conectar con el broker", throwable)
            } else {
                estadoMqtt = "MQTT: Conectado a broker.emqx.io"
                Log.d("MQTT", "Conectado exitosamente a broker.emqx.io")

                mqttClient.subscribeWith()
                    .topicFilter(topico)
                    .callback { publish ->
                        val texto = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                        Log.d("MQTT", "Mensaje recibido en [$topico]: $texto")
                        mensajeRecibidoMqtt = texto
                    }
                    .send()
                    .whenComplete { _, subThrowable ->
                        if (subThrowable != null) {
                            Log.e("MQTT", "Error al suscribirse a $topico", subThrowable)
                        } else {
                            Log.d("MQTT", "Suscrito correctamente al tópico: $topico")
                        }
                    }
            }
        }

        onDispose {
            if (mqttClient.state.isConnected) {
                Log.d("MQTT", "Desconectando cliente MQTT")
                mqttClient.disconnect()
            }
        }
    }

    fun publicarMqtt(jsonPayload: String) {
        if (mqttClient.state.isConnected) {
            mqttClient.publishWith()
                .topic(topico)
                .payload(jsonPayload.toByteArray(StandardCharsets.UTF_8))
                .send()
                .whenComplete { _, pubThrowable ->
                    if (pubThrowable != null) {
                        Log.e("MQTT", "Error al publicar mensaje", pubThrowable)
                    } else {
                        Log.d("MQTT", "Mensaje publicado en [$topico]: $jsonPayload")
                    }
                }
        } else {
            Toast.makeText(context, "MQTT no conectado aún", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Registro en Firebase + MQTT",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = estadoMqtt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
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

                    // Publicar también por MQTT hacia MQTTX
                    val jsonMqtt = """{"id":"$userId","nombre":"${usuario.nombre}","apellido":"${usuario.apellido}","edad":${usuario.edad}}"""
                    publicarMqtt(jsonMqtt)

                    // Escritura en nodo users/{userId}
                    database.child("users").child(userId).setValue(usuario)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Guardado en Firebase y enviado por MQTT", Toast.LENGTH_SHORT).show()
                            nombre = ""
                            apellido = ""
                            edad = ""
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }

                    val updates = hashMapOf<String, Any>(
                        "/users/$userId" to usuario,
                        "/registro-historico/$userId" to usuario
                    )
                    database.updateChildren(updates)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar en Firebase y MQTT")
        }

        OutlinedButton(
            onClick = { publicarMqtt("""{"msg": "Ping desde Android Compose"}""") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enviar prueba rápida a MQTTX")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            text = "Último mensaje recibido ($topico):",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = mensajeRecibidoMqtt,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}