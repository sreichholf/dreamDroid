cd lib
mvn3 install:install-file -Dfile=android-support-v4.jar -DgroupId=android.support -DartifactId=compatibility-v4 -Dversion=r4 -Dpackaging=jar
mvn3 install:install-file -Dfile=flattr-android-sdk-0.0.1.4.jar -DgroupId=com.flattr4android.sdk -DartifactId=flattr-android-sdk -Dversion=0.0.1.4 -Dpackaging=jar
mvn3 install:install-file -Dfile=java-flattr-rest-0.0.1.4.jar -DgroupId=com.flattr4android.rest -DartifactId=java-flattr-rest -Dversion=0.0.1.4 -Dpackaging=jar
