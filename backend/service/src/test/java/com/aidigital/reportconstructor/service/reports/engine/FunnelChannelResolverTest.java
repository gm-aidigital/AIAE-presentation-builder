package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FunnelChannelGroup;
import com.aidigital.reportconstructor.service.reports.dto.TacticFunnelEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FunnelChannelResolverTest {

	@Mock
	CampaignResolvers campaignResolvers;

	@InjectMocks
	FunnelChannelResolver resolver;

	@Test
	void resolveFunnelChannels_shouldGroupTacticsByTheirMediaPlanGoalTest() {
		// Given: three plan lines bought against three different funnel stages, and no hand-entered override
		for (FunnelChannelGroup group : FunnelChannelGroup.values()) {
			lenient().when(campaignResolvers.resolve(List.of(), List.of(), group.label()))
					.thenReturn(new Resolved(group.label(), null, "not_found"));
		}
		List<TacticFunnelEntry> tactics = List.of(
				new TacticFunnelEntry(1, "CTV", "Awareness"),
				new TacticFunnelEntry(2, "Display", "Consideration & Engagement"),
				new TacticFunnelEntry(3, "Search", "Conversions"));

		// When:
		Map<String, Resolved> out = resolver.resolveFunnelChannels(List.of(), List.of(), tactics);

		// Then: each stage carries its own channels, named exactly as the tactic tokens name them
		assertThat(out.get("{{awareness channels}}").value()).isEqualTo("CTV");
		assertThat(out.get("{{consideration channels}}").value()).isEqualTo("Display");
		assertThat(out.get("{{conversions channels}}").value()).isEqualTo("Search");
	}

	@Test
	void resolveFunnelChannels_shouldListATwoStageLineTwiceAndACharacterOnceTest() {
		// Given: a plan line bought for two stages, and the same channel bought on two lines
		for (FunnelChannelGroup group : FunnelChannelGroup.values()) {
			lenient().when(campaignResolvers.resolve(List.of(), List.of(), group.label()))
					.thenReturn(new Resolved(group.label(), null, "not_found"));
		}
		List<TacticFunnelEntry> tactics = List.of(
				new TacticFunnelEntry(1, "Video", "Awareness & Consideration"),
				new TacticFunnelEntry(2, "Display", "Awareness"),
				new TacticFunnelEntry(3, "Display", "Brand awareness"),
				new TacticFunnelEntry(4, "Audio", null));

		// When:
		Map<String, Resolved> out = resolver.resolveFunnelChannels(List.of(), List.of(), tactics);

		// Then: the two-stage line lands in both lists, the repeated channel is named once, and a line with
		// no goal at all is listed nowhere
		assertThat(out.get("{{awareness channels}}").value()).isEqualTo("Video, Display");
		assertThat(out.get("{{consideration channels}}").value()).isEqualTo("Video");
		assertThat(out.get("{{conversions channels}}").value()).isNull();
		assertThat(out.get("{{awareness channels}}").value()).doesNotContain("Audio");
	}

	@Test
	void resolveFunnelChannels_shouldPreferAHandEnteredListOverThePlanTest() {
		// Given: the user rewrote the awareness list in the workbook, while the other two stages are derived
		when(campaignResolvers.resolve(List.of(), List.of(), FunnelChannelGroup.AWARENESS.label()))
				.thenReturn(new Resolved("Awareness channels:", "CTV, Online Video", "sheet"));
		lenient().when(campaignResolvers.resolve(List.of(), List.of(), FunnelChannelGroup.CONSIDERATION.label()))
				.thenReturn(new Resolved("Consideration channels:", null, "not_found"));
		lenient().when(campaignResolvers.resolve(List.of(), List.of(), FunnelChannelGroup.CONVERSIONS.label()))
				.thenReturn(new Resolved("Conversions channels:", null, "not_found"));
		List<TacticFunnelEntry> tactics = List.of(new TacticFunnelEntry(1, "Display", "Awareness"));

		// When:
		Map<String, Resolved> out = resolver.resolveFunnelChannels(List.of(), List.of(), tactics);

		// Then: the typed list wins over the one the plan implies
		assertThat(out.get("{{awareness channels}}").value()).isEqualTo("CTV, Online Video");
	}
}
