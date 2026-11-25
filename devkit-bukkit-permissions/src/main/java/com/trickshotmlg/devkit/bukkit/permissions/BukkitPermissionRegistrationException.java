package com.trickshotmlg.devkit.bukkit.permissions;

/** Raised when a catalog permission conflicts with an incompatible server registration. */
public final class BukkitPermissionRegistrationException extends IllegalStateException {
    private final String permissionNode;

    /** Creates a registration conflict with the affected permission node. */
    public BukkitPermissionRegistrationException(String permissionNode, String message) {
        super(message);
        this.permissionNode = permissionNode;
    }

    /** Creates a registration failure with its underlying cause. */
    public BukkitPermissionRegistrationException(String permissionNode, String message, Throwable cause) {
        super(message, cause);
        this.permissionNode = permissionNode;
    }

    /** The permission node that caused registration to fail.
     * @return conflicting node
     */
    public String permissionNode() { return permissionNode; }
}
