# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in E:\Android\SDK/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Shrink only
-dontobfuscate

# For AOSP internal disclosure in module "fileprovider"
-keepclassmembers class * extends android.content.ContentResolver { *; }
-dontwarn android.content.ContentResolver
-dontwarn android.content.IContentProvider

# Remove verbose and debug logging
-assumenosideeffects class android.util.Log {
	public static boolean isLoggable(java.lang.String, int);
	public static int v(...);
}

# For generics reflection to work
-keepattributes Signature
-keepattributes *Annotation*

# More debugging info (line number)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
