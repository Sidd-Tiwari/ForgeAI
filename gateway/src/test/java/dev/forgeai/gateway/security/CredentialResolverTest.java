package dev.forgeai.gateway.security;

import static org.assertj.core.api.Assertions.*;
import dev.forgeai.gateway.domain.Models.*;
import org.junit.jupiter.api.Test;

class CredentialResolverTest {
    @Test void requestCredentialWinsAndIsCleared(){
        var resolver=new CredentialResolver("platform","",""); char[] exposed;
        try(var c=resolver.resolve(Provider.OPENAI,"sk-secret")){exposed=c.value();assertThat(c.source()).isEqualTo(CredentialSource.REQUEST_BYOK);assertThat(c.toString()).doesNotContain("sk-secret");}
        assertThat(exposed).containsOnly('\0');
    }
    @Test void missingCredentialIsRejected(){assertThatThrownBy(()->new CredentialResolver("","","").resolve(Provider.GEMINI,null)).isInstanceOf(CredentialResolver.MissingCredentialException.class);}
}
