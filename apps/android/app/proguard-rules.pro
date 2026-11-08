# Tinward release shrinking rules.
# Most libraries (Room, Hilt, WorkManager, Compose, Play Billing) ship their own consumer rules.

# SQLCipher: native code looks these classes up by name from C, so they must keep their names.
-keep class net.zetetic.database.** { *; }
-keepclassmembers class net.zetetic.database.** { native <methods>; }

# Apache POI (reads the legacy .xls statements): large and reflective, and none of the optional
# pieces it can reference are present.
-dontwarn org.apache.poi.**
-dontwarn org.apache.commons.**
-dontwarn org.apache.logging.**
-dontwarn org.osgi.**
-dontwarn aQute.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.w3c.dom.**
-dontwarn org.openxmlformats.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.etsi.**
-dontwarn org.w3.**

# Keep readable stack traces for crash reports: line numbers survive, names are mapped.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
