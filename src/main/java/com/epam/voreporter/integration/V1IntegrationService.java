package com.epam.voreporter.integration;

import com.epam.voreporter.domain.Story;
import com.epam.voreporter.domain.Task;
import com.epam.voreporter.utils.FilterBuilder;
import com.versionone.apiclient.Asset;
import com.versionone.apiclient.Query;
import com.versionone.apiclient.Services;
import com.versionone.apiclient.exceptions.APIException;
import com.versionone.apiclient.exceptions.ConnectionException;
import com.versionone.apiclient.exceptions.OidException;
import com.versionone.apiclient.filters.IFilterTerm;
import com.versionone.apiclient.interfaces.IAssetType;
import com.versionone.apiclient.interfaces.IAttributeDefinition;
import com.versionone.apiclient.services.QueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class V1IntegrationService {

    private static final String ASSET_STATE_ACTIVE = "64";

    private IAttributeDefinition storyNameAttribute;
    private IAttributeDefinition storyIdAttribute;
    private IAttributeDefinition storyNumberAttribute;
    private IAttributeDefinition taskStatusAttribute;
    private IAttributeDefinition taskChangedDateAttribute;
    private IAttributeDefinition taskNameAttribute;
    private IAttributeDefinition ownerNamesAttribute;
    private IAttributeDefinition timeboxAssetStateAttribute;
    private IAttributeDefinition teamNameAttribute;
    private IAttributeDefinition taskAssetState;

    private IAssetType taskInfo;
    private Services v1Services;

    @Value("${story.link}")
    private String storyLink;
    @Value("${team.name}")
    private String teamName;
    @Value("${team.members}")
    private String[] memberNames;

    @Autowired
    private V1Connection v1Connection;

    @PostConstruct
    public void setUp() {
        v1Services = new Services(v1Connection.getConnector());
        taskInfo = v1Services.getMeta().getAssetType("Task");
        storyNameAttribute = taskInfo.getAttributeDefinition("Parent.Name");
        storyIdAttribute = taskInfo.getAttributeDefinition("Parent.ID");
        storyNumberAttribute = taskInfo.getAttributeDefinition("Parent.Number");
        taskStatusAttribute = taskInfo.getAttributeDefinition("Status.Name");
        taskChangedDateAttribute = taskInfo.getAttributeDefinition("ChangeDate");
        taskNameAttribute = taskInfo.getAttributeDefinition("Name");
        ownerNamesAttribute = taskInfo.getAttributeDefinition("Owners.Nickname");
        timeboxAssetStateAttribute = taskInfo.getAttributeDefinition("Timebox.AssetState");
        teamNameAttribute = taskInfo.getAttributeDefinition("Team.Name");
        taskAssetState = taskInfo.getAttributeDefinition("AssetState");
    }

    /**
     * Gets set of {@link Story}ies to report using VersionOne API. It builds queries to retrieve all in progress tasks
     * and tasks that were completed between 4pm yesterday and 4pm today.
     *
     * @return set of {@link Story}ies to report
     */
    public Set<Story> getStories() {
        Query query = new Query(taskInfo, false);
        query.getSelection().addAll(Arrays.asList(storyNameAttribute, storyIdAttribute, taskChangedDateAttribute,
                taskStatusAttribute, taskNameAttribute, ownerNamesAttribute, storyNumberAttribute,
                timeboxAssetStateAttribute, taskAssetState));

        int dayOffset = DayOfWeek.MONDAY != LocalDate.now().getDayOfWeek() ? 1 : 3;
        LocalDateTime startDate = LocalDateTime.of(LocalDate.now().minusDays(dayOffset), LocalTime.of(9, 0));
        LocalDateTime finishDate = LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 0));
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

        IFilterTerm completedTasksFilter = new FilterBuilder()
                .addEqual(FilterBuilder.Operator.EQUALORMORE, taskChangedDateAttribute, startDate.format(formatter))
                .addEqual(FilterBuilder.Operator.LESS, taskChangedDateAttribute, finishDate.format(formatter))
                .addEqual(FilterBuilder.Operator.EQUAL, teamNameAttribute, teamName)
                .addEqual(FilterBuilder.Operator.EQUAL, ownerNamesAttribute, memberNames)
                .addEqual(FilterBuilder.Operator.EQUAL, taskStatusAttribute, "Completed")
                .addEqual(FilterBuilder.Operator.EQUAL, timeboxAssetStateAttribute, ASSET_STATE_ACTIVE)
                .addEqual(FilterBuilder.Operator.EQUAL, taskAssetState, ASSET_STATE_ACTIVE)
                .build();
        IFilterTerm inProgressTasksFilter = new FilterBuilder()
                .addEqual(FilterBuilder.Operator.EQUAL, teamNameAttribute, teamName)
                .addEqual(FilterBuilder.Operator.EQUAL, ownerNamesAttribute, memberNames)
                .addEqual(FilterBuilder.Operator.EQUAL, taskStatusAttribute, "In Progress")
                .addEqual(FilterBuilder.Operator.EQUAL, timeboxAssetStateAttribute, ASSET_STATE_ACTIVE)
                .addEqual(FilterBuilder.Operator.EQUAL, taskAssetState, ASSET_STATE_ACTIVE)
                .build();

        Set<Story> stories = new HashSet<>(getStories(completedTasksFilter, query));
        getStories(inProgressTasksFilter, query)
                .forEach(inProgressStory -> {
                    Optional<Story> storyOptional = stories.stream()
                            .filter(story -> story.equals(inProgressStory))
                            .findFirst();
                    if (storyOptional.isPresent()) {
                        storyOptional.get().getTasks().addAll(inProgressStory.getTasks());
                    } else {
                        stories.add(inProgressStory);
                    }
                });
        return stories;
    }

    private Set<Story> getStories(IFilterTerm filter, Query query) {
        query.setFilter(filter);
        Map<String, Story> storyLinkToStoryMap = new HashMap<>();
        for (Asset asset : executeQuery(query).getAssets()) {
            Task task = new Task();
            String link = storyLink + getAttribute(asset, storyIdAttribute).replaceAll("Story:", "");
            Story story = storyLinkToStoryMap.getOrDefault(link, new Story());
            story.setLink(link);
            story.setId(getAttribute(asset, storyNumberAttribute));
            story.setName(getAttribute(asset, storyNameAttribute));
            story.getTasks().add(task);
            task.setOwner(getAttribute(asset, ownerNamesAttribute));
            task.setStatus(getAttribute(asset, taskStatusAttribute));
            task.setTaskName(getAttribute(asset, taskNameAttribute));
            storyLinkToStoryMap.put(link, story);
        }
        return storyLinkToStoryMap
                .values()
                .stream()
                .sorted(Comparator.comparing(Story::getId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String getAttribute(Asset asset, IAttributeDefinition attributeDefinition) {
        try {
            return asset.getAttribute(attributeDefinition).getValue().toString();
        } catch (APIException e) {
            throw new RuntimeException(e);
        }
    }

    private QueryResult executeQuery(Query query) {
        try {
            return v1Services.retrieve(query);
        } catch (ConnectionException | APIException | OidException e) {
            throw new RuntimeException("V1 Integration exception", e);
        }
    }
}
