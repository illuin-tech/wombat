package tech.illuin.wombat.ui;

import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;

import java.util.List;

public class Templates
{
    public record MaxSpan(long millis, String label) {}

    public record AssetSelection(List<Asset> all, List<String> selectedIds) {}

    public record AssetView(AssetIdentity identity, String namespace) {}

    public record EnvironmentView(String id, String name) {}

    public record EnvironmentSelection(List<EnvironmentView> options, String selectedId) {}
}
