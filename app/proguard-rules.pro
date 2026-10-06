# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep,allowobfuscation,allowshrinking class kotlinx.coroutines.flow.Flow
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Room
-dontwarn androidx.room.**
-keep class androidx.room.paging.LimitOffsetDataSource { *; }
-keep class androidx.sqlite.db.SupportSQLiteOpenHelper$Factory
-keepclasseswithmembernames class * {
    @androidx.room.ColumnInfo <fields>;
}

# Gson specific classes
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
# Room Entity와 DAO 클래스 보호
-keep class com.jinscompany.saveurl.domain.model.** { *; }         # 데이터 모델 패키지 경로

# OKHttp3
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Kotlin reflection
-keep class kotlin.reflect.jvm.internal.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
# DataStore Preferences (protobuf-lite는 필드명을 리플렉션으로 찾으므로 필드 보존 필요)
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Gson TypeToken (gson 2.10.1 은 R8 규칙을 내장하지 않음)
# 익명 TypeToken 서브클래스의 제네릭 시그니처가 R8 full mode 에서 제거되면
# "TypeToken must be created with a type argument" 로 크래시하므로 시그니처 보존
-keepattributes Signature
-keep,allowobfuscation class com.google.gson.reflect.TypeToken
-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken
