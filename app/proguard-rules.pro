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

# שומר מספרי שורות ל-stack traces קריאים (חשוב עם דיווח קריסות בהמשך)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, Exceptions

# --- Firestore data models -------------------------------------------------
# Firestore ממפה מסמכים למחלקות דרך reflection (toObject) — חובה לשמור את מודל
# הנתונים במלואו: הבנאי הריק, השדות והגטרים/סטרים, אחרת הדה-סריאליזציה תיכשל בזמן ריצה.
-keep class com.shai.capitall.data.model.** { *; }
-keepclassmembers enum com.shai.capitall.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Gson / Retrofit remote models -----------------------------------------
# מודלי ה-API (Finnhub/Yahoo/בנק ישראל) מפוענחים ע"י Gson לפי שמות שדות — לשמור אותם.
-keep class com.shai.capitall.data.remote.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- Retrofit / OkHttp -----------------------------------------------------
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# --- MPAndroidChart --------------------------------------------------------
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# --- PdfBox-Android (יבוא דפי חשבון מ-PDF) ---------------------------------
# אנחנו מחלצים טקסט בלבד, ולכן מפענחי התמונות האופציונליים אינם נכללים ב-APK.
# PDFBox מפנה אליהם סטטית, ובלי ההתעלמות הזו R8 נכשל על "Missing class".
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**

# PDFBox טוען משאבי גופנים ומפות תווים דרך reflection ומתוך assets
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }