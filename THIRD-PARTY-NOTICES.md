# Third-Party Notices

MinecraftDevKit's shared command framework and the standalone Bukkit command example use Incendo Cloud v2. The command example redistributes the Cloud Core, Cloud Bukkit, Cloud Paper, and Cloud Brigadier runtime modules in its shaded plugin JAR.

## Incendo Cloud

Copyright (c) 2024 Incendo. Licensed under the MIT License.

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Additional shaded dependencies

The command example also redistributes `io.leangen.geantyref:geantyref` and `com.fasterxml.jackson.core:jackson-core`, licensed under Apache License 2.0. The full license is provided in [`THIRD-PARTY-LICENSES/LICENSE-APACHE-2.0.txt`](THIRD-PARTY-LICENSES/LICENSE-APACHE-2.0.txt).

The Paper API is compile-only and is not included in the example plugin JAR. Adventure is supplied by Paper and excluded from the shaded output.
