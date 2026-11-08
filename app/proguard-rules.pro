# JNI entry points must keep their names (release builds currently do not minify, this is for when they do).
-keep class com.interviewcopilot.app.llm.NativeLlm { *; }
-keep class com.interviewcopilot.app.voice.NativeWhisper { *; }
-keep interface com.interviewcopilot.app.llm.TokenSink { *; }
