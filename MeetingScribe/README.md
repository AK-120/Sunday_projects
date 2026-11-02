# Meeting Scribe (Android)

Records a meeting, writes it down in English (from English, Malayalam, Hindi and
more), summarises it with action items, and opens an email to the person you
set in the app. Past meetings are kept on the phone.

All AI runs on **NVIDIA's free hosted models** (build.nvidia.com):

| Step | Model | Endpoint |
|------|-------|----------|
| Speech to text, best (needs a Sarvam key) | Sarvam `saaras` (Indian English `en-IN`, Malayalam `ml-IN`, 10+ Indian languages) | REST, `api.sarvam.ai/speech-to-text` |
| Speech to text, fallback | NVIDIA `parakeet-tdt-0.6b-v2` (English), `whisper-large-v3` (others) | gRPC, `grpc.nvcf.nvidia.com:443` |
| Translation to English, summary | `meta/llama-3.3-70b-instruct` (changeable) | REST, `integrate.api.nvidia.com/v1/chat/completions` |

## Get the APK without Android Studio

Push this folder to a GitHub repo. The workflow in `.github/workflows/build-apk.yml` (repo root)
builds a debug APK; download it from the run's Artifacts, copy it to the phone and install it.

## Run it

1. Open the `MeetingScribe` folder in **Android Studio** (Koala or newer) and let Gradle sync.
2. Run on a phone or emulator (Android 8.0+).
3. Get a free NVIDIA key at https://build.nvidia.com (sign in, open any model, **Get API Key**). For Indian English and Malayalam also get a Sarvam key at https://dashboard.sarvam.ai.
4. In the app: **Settings** (top right) -> paste both keys and the recipient email -> **Save settings**.
5. On the home screen pick the **language spoken in the meeting**, then **Start recording**.
6. **Stop and write notes** -> the notes open automatically -> **Email notes**.

## Why accuracy is better than v1

- English uses Parakeet, an English-only model, instead of Whisper.
- Audio is cut at pauses in speech (not every 30 seconds), so words are not split.
- Silent stretches are dropped; speech models invent text from silence.
- Non-English audio is first written in its own language, then translated by a
  70B language model. This is usually better than Whisper's built-in translation.
- Repeated-phrase glitches and stock hallucinations ("Thank you.") are filtered.

## Malayalam: what to expect

NVIDIA's published speech-model support list does **not** include Malayalam (or
Telugu). The app still sends Malayalam to Whisper, which knows the language, but
accuracy is clearly lower than English. To get the best out of it:

- Put the phone close to the speaker; one speaker at a time.
- Use the "Show Malayalam" switch on a meeting to compare the original text with the English.
- If that is not good enough, the speech step needs a model outside NVIDIA's free set
  (for example Google Cloud Speech-to-Text or AI4Bharat/Sarvam models). `NvidiaAsr.kt`
  is the only file that would change.

## Things to know

- The API key is stored in app-private storage on the phone (not encrypted at rest).
- Email opens your email app with everything filled in; you tap Send. No email password is stored.
- `app/src/main/proto/riva_asr.proto` is a trimmed copy of NVIDIA Riva's definition,
  written from memory. If transcription errors with INVALID_ARGUMENT, compare it with
  `riva/proto/riva_asr.proto` in https://github.com/nvidia-riva/common.
- NVIDIA's free hosted endpoints have rate limits and can be slow at busy times.
