# Readable stack traces in crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# osmdroid has optional integrations that are not on the classpath
-dontwarn org.osmdroid.**

# WorkManager keeps its jobs in a Room database, and Room creates the generated
# WorkDatabase_Impl by reflection. Room 2.6's own rule keeps that class but not its
# constructor, which R8's full mode then removes (crash on start).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
