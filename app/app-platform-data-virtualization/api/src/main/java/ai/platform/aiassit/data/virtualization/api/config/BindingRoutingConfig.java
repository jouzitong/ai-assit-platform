package ai.platform.aiassit.data.virtualization.api.config;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RoutingStrategy;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BindingRoutingConfig {
    private Integer version = 1;
    private RoutingStrategy strategy = RoutingStrategy.SINGLE;
    private List<String> shardFields = new ArrayList<>();
    private HashRouteConfig hash;
    private RangeRouteConfig range;
    private ListRouteConfig list;

    @Data
    public static class HashRouteConfig {
        private Integer modulus;
        private Integer remainder;
    }

    @Data
    public static class RangeRouteConfig {
        private Object lower;
        private Boolean lowerInclusive = true;
        private Object upper;
        private Boolean upperInclusive = false;
    }

    @Data
    public static class ListRouteConfig {
        private List<Object> values = new ArrayList<>();
    }
}
