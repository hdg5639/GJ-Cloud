package gj.cloud.ops.global.crypto;

public record ManagementKeyPair(String publicKeyLine, byte[] privateKeyPemBytes) {
}
