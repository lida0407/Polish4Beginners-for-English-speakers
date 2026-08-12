# Polish4Beginners release rules.
# Conservative on purpose: this app reflects nothing of its own, so the main
# risk is third-party (ML Kit) internals being stripped.

# ---- ML Kit translation -------------------------------------------------
# ML Kit loads model/manager classes dynamically and ships native code.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ML Kit and Play Services rely on annotations at runtime.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ---- App entry point ----------------------------------------------------
-keep class com.example.polishphrasebook.MainActivity { *; }

# The Activity implements TTS callbacks that the framework resolves reflectively.
-keep class * implements android.speech.tts.TextToSpeech$OnInitListener { *; }
-keep class * extends android.speech.tts.UtteranceProgressListener { *; }

# Custom Views constructed from code only, but keep the standard View
# constructors in case any are ever inflated from XML.
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ---- org.json -----------------------------------------------------------
# Provided by the platform; keep R8 quiet about the desktop test artifact.
-dontwarn org.json.**

# ---- Diagnostics --------------------------------------------------------
# Keep line numbers so Play Console crash reports remain readable, but hide
# the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
