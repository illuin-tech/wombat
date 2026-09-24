package tech.illuin.wombat.impact.controller;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.illuin.wombat.connector.boavizta.BoaviztaTestData;
import tech.illuin.wombat.connector.ecologits.EcologitsTestData;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.core.connector.ecologits.connector.EcologitsClient;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;

@QuarkusTest
class SimulationControllerTest
{
    @Inject BoaviztaClient boaviztaClient;
    @Inject EcologitsClient ecologitsClient;

    private static final String LLM_ASSET_JSON = """
        {
          "type": "LLM",
          "activity": {
            "range": {
              "start": "2024-01-01T00:00:00Z",
              "end": "2024-01-02T00:00:00Z"
            },
            "outputTokenCount": 500,
            "requestCount": 100
          },
          "profile": {
            "provider": "mistralai",
            "model": "mistral-large-latest",
            "location": "FRA",
            "request-profile": {
              "output-token-count": 500,
              "request-per-year": 1000000
            }
          }
        }
        """;

    private static final String KUBERNETES_ASSET_JSON = """
        {
          "type": "KUBERNETES",
          "activity": {
            "range": {
              "start": "2024-01-01T00:00:00Z",
              "end": "2024-01-02T00:00:00Z"
            },
            "cpuUsage": 100.0,
            "containerShares": {
              "api": 1.0
            }
          },
          "profile": {
            "provider": "aws",
            "instance-type": "c5.large",
            "location": "FRA",
            "lifespan": 43800
          }
        }
        """;

    @BeforeEach
    void setupMocks()
    {
        Mockito.reset(boaviztaClient, ecologitsClient);
        Mockito.when(boaviztaClient.getInstanceConfig(any(), any())).thenReturn(BoaviztaTestData.fakeInstanceConfig(8));
        Mockito.when(boaviztaClient.getInstanceImpact(anyBoolean(), anyInt(), any(), any())).thenReturn(BoaviztaTestData.fakeImpactResponse());
        Mockito.when(ecologitsClient.estimate(any())).thenReturn(EcologitsTestData.fakeEstimation());
    }

    @Test
    void simulateAll_withLLMSimulatedAsset_returnsImpactEvaluation()
    {
        String body = "{\"scopes\": [\"IMPACT\"], \"assets\": [" + LLM_ASSET_JSON + "]}";

        given()
            .contentType("application/json").body(body)
            .when().post("/simulation")
            .then()
            .statusCode(200)
            .body("payload.size()", is(1))
            .body("payload[0].provider", is("ECOLOGITS"))
            .body("payload[0].footprint.gwp", notNullValue())
            .body("payload[0].footprint.pe", notNullValue())
            .body("payload[0].footprint.adp", notNullValue())
            .body("payload[0].serviceImpacts.size()", greaterThan(0))
            .body("payload[0].serviceImpacts[0].assetType", is("LLM_SIMULATED"))
            .body("payload[0].serviceImpacts[0].profile.model", is("mistral-large-latest"));
    }

    @Test
    void simulateAll_withKubernetesSimulatedAsset_returnsImpactEvaluation()
    {
        String body = "{\"scopes\": [\"IMPACT\"], \"assets\": [" + KUBERNETES_ASSET_JSON + "]}";

        given()
            .contentType("application/json").body(body)
            .when().post("/simulation")
            .then()
            .statusCode(200)
            .body("payload.size()", is(1))
            .body("payload[0].provider", is("BOAVIZTA"))
            .body("payload[0].footprint.gwp", notNullValue())
            .body("payload[0].serviceImpacts.size()", greaterThan(0))
            .body("payload[0].serviceImpacts[0].assetType", is("KUBERNETES_SIMULATED"));
    }

    @Test
    void simulateAll_withMultipleAssets_returnsAllEvaluations()
    {
        String body = "{\"scopes\": [\"IMPACT\"], \"assets\": [" + LLM_ASSET_JSON + ", " + KUBERNETES_ASSET_JSON + "]}";

        given()
            .contentType("application/json").body(body)
            .when().post("/simulation")
            .then()
            .statusCode(200)
            .body("payload.size()", is(2))
            .body("payload.provider", containsInAnyOrder("ECOLOGITS", "BOAVIZTA"));
    }

    @Test
    void simulateAll_withCostScopeOnly_returnsCostEvaluations()
    {
        String body = "{\"scopes\": [\"COST\"], \"assets\": [" + LLM_ASSET_JSON + "]}";

        given()
            .contentType("application/json").body(body)
            .when().post("/simulation")
            .then()
            .statusCode(200)
            .body("payload.size()", is(1))
            .body("payload[0].environmentId", is("simulated-environment"))
            .body("payload[0].assetId", is("simulated-asset"));
    }

    @Test
    void simulateAll_withImpactAndCostScopes_returnsBothEvaluations()
    {
        String body = "{\"scopes\": [\"IMPACT\", \"COST\"], \"assets\": [" + LLM_ASSET_JSON + "]}";

        given()
            .contentType("application/json").body(body)
            .when().post("/simulation")
            .then()
            .statusCode(200)
            .body("payload.size()", is(2))
            .body("payload[0].provider", is("ECOLOGITS"))
            .body("payload[1].environmentId", is("simulated-environment"))
            .body("payload[1].assetId", is("simulated-asset"));
    }

    @Test
    void simulateAll_withEmptyAssets_returnsEmptyList()
    {
        given()
            .contentType("application/json").body("{}")
            .when().post("/simulation")
            .then()
            .statusCode(200)
            .body("payload.size()", is(0));

        given()
            .contentType("application/json").body("{\"assets\": []}")
            .when().post("/simulation")
            .then()
            .statusCode(200)
            .body("payload.size()", is(0));
    }

    @Test
    void simulateAll_withMissingActivity_returns400()
    {
        String llmNoActivity = """
            {
              "type": "LLM",
              "profile": {
                "provider": "mistralai",
                "model": "mistral-large-latest",
                "location": "FRA",
                "request-profile": {
                  "output-token-count": 500,
                  "request-per-year": 1000000
                }
              }
            }
            """;

        String kubernetesNoActivity = """
            {
              "type": "KUBERNETES",
              "profile": {
                "provider": "aws",
                "instance-type": "c5.large",
                "location": "FRA",
                "lifespan": 43800
              }
            }
            """;

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + llmNoActivity + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + kubernetesNoActivity + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);
    }

    @Test
    void simulateAll_withNullActivity_returns400()
    {
        String llmNullActivity = """
            {
              "type": "LLM",
              "profile": {
                "provider": "mistralai",
                "model": "mistral-large-latest",
                "location": "FRA",
                "request-profile": {
                  "output-token-count": 500,
                  "request-per-year": 1000000
                }
              },
              "activity": null
            }
            """;

        String kubernetesNullActivity = """
            {
              "type": "KUBERNETES",
              "profile": {
                "provider": "aws",
                "instance-type": "c5.large",
                "location": "FRA",
                "lifespan": 43800
              },
              "activity": null
            }
            """;

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + llmNullActivity + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + kubernetesNullActivity + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);
    }

    @Test
    void simulateAll_withNullAssetInList_returns400()
    {
        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [null]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);
    }

    @Test
    void simulateAll_withNullOrMissingKubernetesContainerShares_returns400()
    {
        String kubernetesNullContainerShares = """
            {
              "type": "KUBERNETES",
              "profile": {
                "provider": "aws",
                "instance-type": "c5.large",
                "location": "FRA",
                "lifespan": 43800
              },
              "activity": {
                "range": {
                  "start": "2024-01-01T00:00:00Z",
                  "end": "2024-01-02T00:00:00Z"
                },
                "cpuUsage": 100.0,
                "containerShares": null
              }
            }
            """;

        String kubernetesMissingContainerShares = """
            {
              "type": "KUBERNETES",
              "profile": {
                "provider": "aws",
                "instance-type": "c5.large",
                "location": "FRA",
                "lifespan": 43800
              },
              "activity": {
                "range": {
                  "start": "2024-01-01T00:00:00Z",
                  "end": "2024-01-02T00:00:00Z"
                },
                "cpuUsage": 100.0
              }
            }
            """;

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + kubernetesNullContainerShares + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + kubernetesMissingContainerShares + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);
    }

    @Test
    void simulateAll_withNullOrMissingRange_returns400()
    {
        String llmNullRange = """
            {
              "type": "LLM",
              "profile": {
                "provider": "mistralai",
                "model": "mistral-large-latest",
                "location": "FRA",
                "request-profile": {
                  "output-token-count": 500,
                  "request-per-year": 1000000
                }
              },
              "activity": {
                "range": null,
                "outputTokenCount": 500,
                "requestCount": 100
              }
            }
            """;

        String llmMissingRange = """
            {
              "type": "LLM",
              "profile": {
                "provider": "mistralai",
                "model": "mistral-large-latest",
                "location": "FRA",
                "request-profile": {
                  "output-token-count": 500,
                  "request-per-year": 1000000
                }
              },
              "activity": {
                "outputTokenCount": 500,
                "requestCount": 100
              }
            }
            """;

        String kubernetesNullRange = """
            {
              "type": "KUBERNETES",
              "profile": {
                "provider": "aws",
                "instance-type": "c5.large",
                "location": "FRA",
                "lifespan": 43800
              },
              "activity": {
                "range": null,
                "cpuUsage": 100.0,
                "containerShares": {
                  "api": 1.0
                }
              }
            }
            """;

        String kubernetesMissingRange = """
            {
              "type": "KUBERNETES",
              "profile": {
                "provider": "aws",
                "instance-type": "c5.large",
                "location": "FRA",
                "lifespan": 43800
              },
              "activity": {
                "cpuUsage": 100.0,
                "containerShares": {
                  "api": 1.0
                }
              }
            }
            """;

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + llmNullRange + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + llmMissingRange + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + kubernetesNullRange + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + kubernetesMissingRange + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);
    }

    @Test
    void simulateAll_withInvalidRange_returns400()
    {
        String llmNullStart = """
            {
              "type": "LLM",
              "profile": {
                "provider": "mistralai",
                "model": "mistral-large-latest",
                "location": "FRA",
                "request-profile": {
                  "output-token-count": 500,
                  "request-per-year": 1000000
                }
              },
              "activity": {
                "range": {
                  "start": null,
                  "end": "2024-01-02T00:00:00Z"
                },
                "outputTokenCount": 500,
                "requestCount": 100
              }
            }
            """;

        String llmNullEnd = """
            {
              "type": "LLM",
              "profile": {
                "provider": "mistralai",
                "model": "mistral-large-latest",
                "location": "FRA",
                "request-profile": {
                  "output-token-count": 500,
                  "request-per-year": 1000000
                }
              },
              "activity": {
                "range": {
                  "start": "2024-01-01T00:00:00Z",
                  "end": null
                },
                "outputTokenCount": 500,
                "requestCount": 100
              }
            }
            """;

        String llmStartEqualsEnd = """
            {
              "type": "LLM",
              "profile": {
                "provider": "mistralai",
                "model": "mistral-large-latest",
                "location": "FRA",
                "request-profile": {
                  "output-token-count": 500,
                  "request-per-year": 1000000
                }
              },
              "activity": {
                "range": {
                  "start": "2024-01-01T00:00:00Z",
                  "end": "2024-01-01T00:00:00Z"
                },
                "outputTokenCount": 500,
                "requestCount": 100
              }
            }
            """;

        String kubernetesStartAfterEnd = """
            {
              "type": "KUBERNETES",
              "profile": {
                "provider": "aws",
                "instance-type": "c5.large",
                "location": "FRA",
                "lifespan": 43800
              },
              "activity": {
                "range": {
                  "start": "2024-01-02T00:00:00Z",
                  "end": "2024-01-01T00:00:00Z"
                },
                "cpuUsage": 100.0,
                "containerShares": {
                  "api": 1.0
                }
              }
            }
            """;

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + llmNullStart + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + llmNullEnd + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + llmStartEqualsEnd + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);

        given()
            .contentType("application/json")
            .body("{\"scopes\": [\"IMPACT\"], \"assets\": [" + kubernetesStartAfterEnd + "]}")
            .when().post("/simulation")
            .then()
            .statusCode(400);
    }

    @Test
    void simulateAll_whenEvaluationThrowsException_returns500()
    {
        Mockito.when(boaviztaClient.getInstanceConfig(any(), any())).thenThrow(new RuntimeException("Downstream error"));

        String body = "{\"scopes\": [\"IMPACT\"], \"assets\": [" + KUBERNETES_ASSET_JSON + "]}";

        given()
            .contentType("application/json").body(body)
            .when().post("/simulation")
            .then()
            .statusCode(500);
    }
}
