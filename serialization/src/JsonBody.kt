package klite.serialization

import klite.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType

@ExperimentalSerializationApi
class JsonBody(
  val json: Json = Json {
    ignoreUnknownKeys = true
  },
  override val contentType: String = "application/json"
): BodyParser, BodyRenderer, Extension {
  @Suppress("UNCHECKED_CAST")
  override fun <T: Any> parse(input: InputStream, type: KType): T = json.decodeFromStream(serializer(type), input) as T
  override fun render(output: OutputStream, value: Any?) = when (value) {
    is ErrorResponse -> json.encodeToStream(ErrorResponseSerializer, value, output)
    else -> json.encodeToStream(serializer(value!!.javaClass), value, output)
  }

  override fun install(server: Server) = server.run {
    registry.register(json)
    renderers += this@JsonBody
    parsers += this@JsonBody
  }
}

@ExperimentalSerializationApi
object ErrorResponseSerializer: KSerializer<ErrorResponse> {
  private val serializer = SerializableErrorResponse.serializer()
  override val descriptor = SerialDescriptor("ErrorResponse", serializer.descriptor)
  override fun serialize(encoder: Encoder, value: ErrorResponse) =
    encoder.encodeSerializableValue(serializer, SerializableErrorResponse(
      type = value.type?.toString(),
      title = value.title,
      status = value.status.value,
      detail = value.detail,
      instance = value.instance,
    ))
  override fun deserialize(decoder: Decoder): ErrorResponse = error("No need")

  @Serializable class SerializableErrorResponse(
    val type: String?,
    val title: String?,
    val status: Int,
    val detail: String?,
    val instance: String? = null
  )
}

class UUIDSerializer: ConverterSerializer<UUID>(UUID::class)

abstract class ConverterSerializer<T: Any>(val type: KClass<T>): KSerializer<T> {
  override val descriptor = PrimitiveSerialDescriptor(type.simpleName!!, PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
  override fun deserialize(decoder: Decoder): T = Converter.from(decoder.decodeString(), type)
}
