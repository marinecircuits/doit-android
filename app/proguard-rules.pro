# kotlinx.serialization ships its own R8 rules; Room, WorkManager and Glance do too.
# Keep line numbers so release stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
