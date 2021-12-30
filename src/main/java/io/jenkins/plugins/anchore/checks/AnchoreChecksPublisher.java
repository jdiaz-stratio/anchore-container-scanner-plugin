package io.jenkins.plugins.anchore.checks;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.anchore.jenkins.plugins.anchore.AnchoreAction;
import com.anchore.jenkins.plugins.anchore.Util;
import com.anchore.jenkins.plugins.anchore.Util.GATE_SUMMARY_COLUMN;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksOutput;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@Restricted(NoExternalUse.class)
public class AnchoreChecksPublisher {

    private final String checksName;
    private final AnchoreAction action;
    private final Run run;
    private final Util.GATE_ACTION finalAction;

    public AnchoreChecksPublisher(final Run run, final String checksName, final Util.GATE_ACTION finalAction) {
        this.run = run;
        this.checksName = checksName;
        this.finalAction = finalAction;
        action = run.getActions(AnchoreAction.class).get(0);
    }

    public void publishChecks(TaskListener listener) {
        ChecksPublisher publisher = ChecksPublisherFactory.fromRun(run, listener);
        publisher.publish(extractChecksDetails());
    }

    private ChecksDetails extractChecksDetails() {
        ChecksOutput output = new ChecksOutput.ChecksOutputBuilder()
                .withTitle("Anchore Gate")
                .withSummary("Anchore analysis finished")
                .withText(extractChecksText())
                .build();

        ChecksConclusion conclusion;
        switch(finalAction) {
        case GO:
        case PASS:
            conclusion = ChecksConclusion.SUCCESS;
            break;
        case WARN:
            conclusion = ChecksConclusion.NEUTRAL;
            break;
        case STOP:
        case FAIL:
        default:
            conclusion = ChecksConclusion.FAILURE;
        }

        return new ChecksDetails.ChecksDetailsBuilder()
                .withName(checksName)
                .withStatus(ChecksStatus.COMPLETED)
                .withConclusion(conclusion)
                .withDetailsURL(run.getAbsoluteUrl() + "anchore-results")
                .withOutput(output)
                .build();
    }

    private String extractChecksText() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        JSONObject summary = action.getGateSummary();
        JSONArray headerList = (JSONArray) summary.get("header");
        StringJoiner joiner = new StringJoiner(" | ", "| ", " |");
        List<String> headers = headerList.stream().map(oneHeader -> (String)((JSONObject)oneHeader).get("title")).collect(Collectors.toList());
        headers.forEach(joiner::add);
        builder.append(joiner);
        builder.append("\n");
        StringJoiner headerSeparationJoiner = new StringJoiner(" | ", "| ", " |");
        headers.forEach(oneHeader -> headerSeparationJoiner.add("---"));
        builder.append(headerSeparationJoiner);
        builder.append("\n");

        JSONArray rowList = (JSONArray) summary.get("rows");
        rowList.forEach(oneRow -> {
            StringJoiner oneJoiner = new StringJoiner(" | ", "| ", " |");
            oneJoiner.add(((JSONObject)oneRow).get(GATE_SUMMARY_COLUMN.Repo_Tag.toString()).toString());
            oneJoiner.add(((JSONObject)oneRow).get(GATE_SUMMARY_COLUMN.Stop_Actions.toString()).toString());
            oneJoiner.add(((JSONObject)oneRow).get(GATE_SUMMARY_COLUMN.Warn_Actions.toString()).toString());
            oneJoiner.add(((JSONObject)oneRow).get(GATE_SUMMARY_COLUMN.Go_Actions.toString()).toString());
            oneJoiner.add(((JSONObject)oneRow).get(GATE_SUMMARY_COLUMN.Final_Action.toString()).toString());
            builder.append(oneJoiner);
            builder.append("\n");
        });
        return builder.toString();
    }

}
