package gj.cloud.ops.application.filebrowser;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.filebrowser.dto.FileStreamTicketPayload;
import gj.cloud.ops.application.vmclient.VmServiceClient;
import gj.cloud.ops.application.vmclient.dto.VmContextResponse;
import gj.cloud.ops.global.crypto.AesGcmCipher;
import gj.cloud.ops.global.ssh.VmSshSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStreamTicketServiceTest {

    private static final String VM_ID = "8d9e4608-16b8-4f23-a20b-6c5e74d720e1";
    private static final String TOKEN = "raw-user-access-token";

    @Test
    void decryptsTokenOnlyForPermissionRevalidation() throws Exception {
        VmServiceClient vmServiceClient = mock(VmServiceClient.class);
        VmSshSessionFactory sshSessionFactory = mock(VmSshSessionFactory.class);
        SftpPathResolver pathResolver = mock(SftpPathResolver.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AesGcmCipher cipher = new AesGcmCipher("0123456789abcdef0123456789abcdef");
        FileStreamTicketService service = new FileStreamTicketService(
                vmServiceClient, sshSessionFactory, pathResolver, redisTemplate, objectMapper, cipher);

        String ciphertext = cipher.encrypt(TOKEN.getBytes(StandardCharsets.UTF_8));
        FileStreamTicketPayload payload = new FileStreamTicketPayload(
                VM_ID, "/home/ubuntu/video.mp4", "10.0.0.2", 1024L, ciphertext);
        String json = objectMapper.writeValueAsString(payload);

        assertThat(json).doesNotContain(TOKEN).contains("bearerTokenCiphertext");
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("file-stream-ticket:ticket-id")).thenReturn(json);
        when(vmServiceClient.getContext(TOKEN, VM_ID)).thenReturn(new VmContextResponse(
                VM_ID, "owner-id", "10.0.0.2", "RUNNING", "MEMBER", List.of("FILE_READ")));

        assertThat(service.validate("ticket-id", VM_ID)).contains(payload);
        verify(vmServiceClient).getContext(TOKEN, VM_ID);
    }
}
