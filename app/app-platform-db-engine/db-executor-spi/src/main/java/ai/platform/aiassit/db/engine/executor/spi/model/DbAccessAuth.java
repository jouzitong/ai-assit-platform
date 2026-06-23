package ai.platform.aiassit.db.engine.executor.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbAccessAuth {

    private String username;

    private String password;

    private String credentialRef;
}
