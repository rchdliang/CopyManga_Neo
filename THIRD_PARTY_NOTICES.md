# Third-party notices

CopyManga Neo retains its upstream GNU General Public License version 3; see LICENSE.

The novel pagination, directory and text selection components retain the original Kixyu / kixyubook MIT copyright notices in their source headers. The complete upstream MIT notice is included unchanged in:

- app/src/main/assets/licenses/kixyubook-MIT.txt (Android)
- shared/src/commonMain/composeResources/files/licenses/kixyubook-MIT.txt (all Compose Multiplatform distributions)

Shared application resources also include CopyMangaNeo-GPL-3.0.txt. The migration baseline is rchdliang/CopyManga_Neo commit 466cef7c6acd2f082a9f7432cc06a082d83bc1c9.

Third-party binary dependencies retain their own respective licenses and package metadata.

The desktop Windows sensitive clipboard adapter uses Java Native Access (JNA) core 5.19.1, distributed through Maven Central by the JNA project. JNA is dual-licensed under LGPL-2.1-or-later or Apache-2.0; this integration uses its Apache-2.0 option. Its upstream license and notices remain in the dependency distribution. Source and license information: https://github.com/java-native-access/jna and https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.19.1/jna-5.19.1.pom .
