package com.meetingscribe.app

import com.google.protobuf.ByteString
import com.meetingscribe.riva.AudioEncoding
import com.meetingscribe.riva.RecognitionConfig
import com.meetingscribe.riva.RecognizeRequest
import com.meetingscribe.riva.RivaSpeechRecognitionGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Speech to text on NVIDIA's free hosted endpoints (gRPC).
 *
 * - English: Parakeet TDT 0.6b v2, an English-only model that is far more
 *   accurate on English than Whisper.
 * - Every other language: Whisper Large v3 in its own language (not its built-in
 *   translate mode). Translation to English is a separate, better step done by
 *   a large language model, see [NvidiaChat.translateToEnglish].
 */
class NvidiaAsr(private val apiKey: String) : SpeechToText {

    private val channel: ManagedChannel = ManagedChannelBuilder
        .forAddress(HOST, 443)
        .useTransportSecurity()
        .maxInboundMessageSize(8 * 1024 * 1024)
        .build()

    private fun stubFor(functionId: String): RivaSpeechRecognitionGrpc.RivaSpeechRecognitionBlockingStub {
        val headers = Metadata().apply {
            put(Metadata.Key.of("function-id", Metadata.ASCII_STRING_MARSHALLER), functionId)
            put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer $apiKey")
        }
        return RivaSpeechRecognitionGrpc.newBlockingStub(channel)
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
    }

    private val parakeet by lazy { stubFor(PARAKEET_EN_FUNCTION_ID) }
    private val whisper by lazy { stubFor(WHISPER_FUNCTION_ID) }

    /** Returns text in the spoken language for a chunk of 16 kHz mono 16-bit PCM audio. */
    override suspend fun transcribe(pcm: ByteArray, languageCode: String): String = withContext(Dispatchers.IO) {
        val english = languageCode == "en"
        val cfg = RecognitionConfig.newBuilder()
            .setEncoding(AudioEncoding.LINEAR_PCM)
            .setSampleRateHertz(PcmRecorder.SAMPLE_RATE)
            .setLanguageCode(if (english) "en-US" else languageCode)
            .setMaxAlternatives(1)
            .setAudioChannelCount(1)

        val request = RecognizeRequest.newBuilder()
            .setConfig(cfg)
            .setAudio(ByteString.copyFrom(pcm))
            .build()

        val stub = if (english) parakeet else whisper
        val response = stub.withDeadlineAfter(120, TimeUnit.SECONDS).recognize(request)
        response.resultsList
            .mapNotNull { it.alternativesList.firstOrNull()?.transcript?.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    override fun close() {
        channel.shutdown()
        runCatching { channel.awaitTermination(2, TimeUnit.SECONDS) }
    }

    companion object {
        private const val HOST = "grpc.nvcf.nvidia.com"

        // Function ids shown on each model's API tab on build.nvidia.com.
        private const val WHISPER_FUNCTION_ID = "b702f636-f60c-4a3d-a6f4-f3568c13bd7d" // openai/whisper-large-v3
        private const val PARAKEET_EN_FUNCTION_ID = "d3fe9151-442b-4204-a70d-5fcc597fd610" // nvidia/parakeet-tdt-0.6b-v2
    }
}
