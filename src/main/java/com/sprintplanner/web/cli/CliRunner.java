package com.sprintplanner.web.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintplanner.domain.dto.PlanningRequest;
import com.sprintplanner.domain.port.in.PlanningInputPort;
import com.sprintplanner.domain.port.out.PlanPublisher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "sprintplanner.cli.enabled", havingValue = "true")
public class CliRunner implements CommandLineRunner {

    private final PlanningInputPort planningInputPort;
    private final PlanPublisher planPublisher;
    private final ObjectMapper objectMapper;

    public CliRunner(PlanningInputPort planningInputPort, PlanPublisher planPublisher, ObjectMapper objectMapper) {
        this.planningInputPort = planningInputPort;
        this.planPublisher = planPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        CliArguments cliArguments = parseArguments(args);
        if (cliArguments == null) {
            return;
        }

        PlanningRequest request = readRequest(cliArguments);
        var sprintPlan = planningInputPort.planSprint(request);
        planPublisher.publish(request.teamId(), sprintPlan);
    }

    private CliArguments parseArguments(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        String file = null;
        boolean runCli = false;
        for (String arg : args) {
            if ("--cli".equals(arg)) {
                runCli = true;
            } else if (arg.startsWith("--file=")) {
                file = arg.substring("--file=".length());
            }
        }

        if (!runCli && !StringUtils.hasText(file)) {
            return null;
        }
        return new CliArguments(file);
    }

    private PlanningRequest readRequest(CliArguments cliArguments) throws IOException {
        if (!StringUtils.hasText(cliArguments.file())) {
            throw new IllegalArgumentException("--file=<path> is required when running with --cli.");
        }

        Path path = Path.of(cliArguments.file());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Input file not found: " + path.toAbsolutePath());
        }

        String content = Files.readString(path);
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("Input file is empty: " + path.toAbsolutePath());
        }

        if (cliArguments.file().endsWith(".json")) {
            return objectMapper.readValue(content, PlanningRequest.class);
        }

        throw new IllegalArgumentException("Unsupported input file type. Use .json for PlanningRequest input.");
    }

    private record CliArguments(String file) {}
}
