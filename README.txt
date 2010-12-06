=====================================================
 AndroidNotes - Android Opera Link sample application
=====================================================

This is an very simple sample application to manage your Opera Link
notes from your Android phone.

Quick instructions for building and running
===========================================

For Ubuntu/Debian.

1. Install Java and Maven
$> sudo apt-get install sun-java6-jdk maven

2. Download the Android SDK
Download it from Google here http://developer.android.com/sdk/index.html
Unpack it into /opt folder.

Run the SDK and AVD manager program
$> cd /opt/android-sdk-linux_x86/tools
$> ./android &

Use the UI to create an AVD called 'em21' and choose Android platform
version 2.1 and the default options. A memory card of 64MB will be plenty.

3. Set environment variables
export JAVA_HOME=/usr/lib/jvm/java-6-sun
export ADK_HOME=/opt/android-sdk-linux_x86/
export PATH=$PATH:$JAVA_HOME/bin:$ADK_HOME:$ADK_HOME/tools
export ANDROID_HOME=$ADK_HOME

4. Grab the source code for the Opera Link Java Client and build it
$> cd ~ && git clone git://github.com/operasoftware/JavaOperaLinkClient
$> cd JavaOperaLinkClient
$> mvn install

After some whirring, the client library will be built and deployed to your
local maven repository in the ~/.m2/repository folder

5. Grab the source code the the AndroidNotes application and build it
$> cd ~ && git clone git://github.com/operasoftware/AndroidNotes
$> cd AndroidNotes
$> mvn package

Some whirring will happen, and the .apk file should be produced in the target/
folder.

6. Run it
$> mvn android:emulator-start
Be patient. It can take some time before the emulator boots to the home screen.
When the emulator is fully booted.
$> mvn android:deploy
Will deploy the app. Open the phone's applications menu and launch the Android
Notes app. On first run, you can click the "Menu" button and then choose 
"Synchronize" to initiate the OAuth authentication process.  

Known issues
============
* When you create a new note or modify a note, switch to another
  application and come back, you lose any unsaved changes
* Deleting notes from *other* clients doesn't make them disappear from
  the Android application
* Authentication doesn't work with Opera Mini, you have to use the
  default browser
* From the note list, if you long-press on a note and select "Delete",
  you get the Preferences dialog instead of getting the note deleted
* The "Synchronize with Opera Notes" option and the "Sync
  Automatically" preference behave a bit erratically

License
=======
The source code included in this distribution is distributed under the
BSD license:

Copyright © 2010, Opera Software
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* Neither the name of Opera Software nor the names of its contributors
  may be used to endorse or promote products derived from this
  software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
