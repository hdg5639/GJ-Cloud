package gj.cloud.ops.application.deployment.repoanalysis;

import java.util.Map;
import java.util.Set;

public record PackageJsonInfo(
        String name,
        boolean isPrivate,
        String type,
        Map<String, String> scripts,
        Set<String> dependencies,
        Set<String> devDependencies,
        boolean hasWorkspaces,
        String packageManager,
        Map<String, String> engines
) {
    public boolean hasScript(String name) {
        return scripts != null && scripts.containsKey(name);
    }

    public boolean hasDependency(String name) {
        return (dependencies != null && dependencies.contains(name))
                || (devDependencies != null && devDependencies.contains(name));
    }
}
