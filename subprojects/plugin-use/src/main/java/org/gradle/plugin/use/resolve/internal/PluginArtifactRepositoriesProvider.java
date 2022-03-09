/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
<<<<<<< HEAD
package org.gradle.plugin.use.resolve.internal;

=======

<<<<<<< HEAD:subprojects/file-watching/src/main/java/org/gradle/internal/watch/vfs/FileChangeListener.java
package org.gradle.internal.watch.vfs;

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.watch.registry.FileWatcherRegistry;

@EventScope(Scopes.UserHome.class)
public interface FileChangeListener extends FileWatcherRegistry.ChangeHandler {
=======
>>>>>>> release
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Provides access to the shared artifact repositories to use for plugin resolution for this build.
 */
@ServiceScope(Scopes.Build.class)
public interface PluginArtifactRepositoriesProvider {
    PluginArtifactRepositories createPluginResolveRepositories();
<<<<<<< HEAD
=======
>>>>>>> release:subprojects/plugin-use/src/main/java/org/gradle/plugin/use/resolve/internal/PluginArtifactRepositoriesProvider.java
>>>>>>> release
}
