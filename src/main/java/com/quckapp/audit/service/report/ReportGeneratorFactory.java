package com.quckapp.audit.service.report;

import com.quckapp.audit.domain.entity.ComplianceReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReportGeneratorFactory {

    private final List<ReportGenerator> generators;

    private Map<ComplianceReport.ReportType, ReportGenerator> generatorMap;

    public ReportGenerator getGenerator(ComplianceReport.ReportType reportType) {
        if (generatorMap == null) {
            generatorMap = generators.stream()
                .collect(Collectors.toMap(
                    ReportGenerator::getReportType,
                    Function.identity()
                ));
        }
        ReportGenerator generator = generatorMap.get(reportType);
        if (generator == null) {
            throw new IllegalArgumentException("No generator found for report type: " + reportType);
        }
        return generator;
    }
}
