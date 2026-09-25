package marca.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CNPJResponse(

        @JsonProperty("descricao_situacao_cadastral")
        String registrationStatus,

        @JsonProperty("municipio")
        String city,

        @JsonProperty("bairro")
        String neighborhood,

        @JsonProperty("logradouro")
        String address,

        @JsonProperty("numero")
        String numberAddress,

        @JsonProperty("uf")
        String uf,

        @JsonProperty("cep")
        String cep,

        @JsonProperty("razao_social")
        String registeredCompanyName,

        @JsonProperty("nome_fantasia")
        String fantasyName,

        @JsonProperty("ddd_telefone_1")
        String telephone

) {}
