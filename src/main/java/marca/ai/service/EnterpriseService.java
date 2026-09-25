package marca.ai.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import marca.ai.exception.EnterpriseException;
import marca.ai.exception.ValidationException;
import marca.ai.service.client.CNPJClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;

@ApplicationScoped
public class EnterpriseService {


    private final CNPJClient CNPJClient;

    public EnterpriseService(@RestClient CNPJClient CNPJClient) {
        this.CNPJClient = CNPJClient;
    }

    public Uni<Void> createEnterprise() {

        return CNPJClient.findCNPJ("0294211")
                .ifNoItem().after(Duration.ofSeconds(3)).fail()
                .onFailure(failure -> !(failure instanceof EnterpriseException || failure instanceof ValidationException))
                .retry().withBackOff(Duration.ofMillis(200)).atMost(3)
                .chain(cnpjResponse -> {

                    return Uni.createFrom().voidItem();
                }).replaceWithVoid();
    }
}
