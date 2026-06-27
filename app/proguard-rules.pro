# Default Android optimize rules apply via proguard-android-optimize.txt.
# Add app-specific keep rules here as the codebase grows.

# Firebase / Crashlytics keep useful line info for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Models deserialized by Firestore reflection get added in later phases; keep
# their no-arg constructors then. Nothing to keep yet in Phase 0.
