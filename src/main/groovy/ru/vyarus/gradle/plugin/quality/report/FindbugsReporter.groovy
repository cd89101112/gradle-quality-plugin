package ru.vyarus.gradle.plugin.quality.report

import org.gradle.api.Project
import ru.vyarus.gradle.plugin.quality.ConfigLoader

/**
 * Prints findbugs errors (from xml report) into console and generates html report using custom xsl.
 *
 * @author Vyacheslav Rusakov
 * @since 12.11.2015
 */
class FindbugsReporter implements Reporter {

    ConfigLoader configLoader

    FindbugsReporter(ConfigLoader configLoader) {
        this.configLoader = configLoader
    }

    @Override
    void report(Project project, String type) {
        project.with {
            File reportFile = file("${extensions.findbugs.reportsDir}/${type}.xml")
            if (!reportFile.exists()) {
                return
            }

            Node result = new XmlParser().parse(reportFile)
            int cnt = result.BugInstance.size()
            if (cnt > 0) {
                Node summary = result.FindBugsSummary[0]
                int fileCnt = summary.FileStats.findAll { (it.@bugCount as Integer) > 0 }.size()
                int p1 = summary.@priority_1 == null ? 0 : summary.@priority_1 as Integer
                int p2 = summary.@priority_2 == null ? 0 : summary.@priority_2 as Integer
                int p3 = summary.@priority_3 == null ? 0 : summary.@priority_3 as Integer
                logger.error "\n$cnt ($p1 / $p2 / $p3) FindBugs violations were found in ${fileCnt} files"

                Map<String, String> desc = buildDescription(result)
                Map<String, String> cat = buildCategories(result)
                result.BugInstance.each { bug ->
                    Node msg = bug.LongMessage[0]
                    Node src = bug.SourceLine[0]
                    String description = ReportUtils.unescapeHtml(desc[bug.@type])
                    String srcPosition = src.@start == src.@end ? src.@start : "${src.@start}-${src.@end}"
                    logger.error "\n[${cat[bug.@category]} | ${bug.@type}] ${src.@classname}:${srcPosition}  " +
                            "(priority ${bug.@priority})" +
                            "\n\t>> ${msg.text()}" +
                            "\n  ${description}"
                }

                // html report
                String htmlReportPath = "${extensions.findbugs.reportsDir}/${type}.html"
                ant.xslt(in: reportFile,
                        style: configLoader.resolveFindbugsXsl(),
                        out: htmlReportPath
                )
                String htmlReportUrl = ReportUtils.toConsoleLink(file(htmlReportPath))
                logger.error "\nFindbugs HTML report: $htmlReportUrl"
            }
        }
    }

    private Map<String, String> buildDescription(Node result) {
        Map<String, String> desc = [:]
        result.BugPattern.each { pattern ->
            desc[pattern.@type] = pattern.Details.text()
            //remove html tags
                    .replaceAll('<(.|\n)*?>', '')
            // remove empty lines after tags remove (only one separator lne remain)
                    .replaceAll('( *\n){3,}', '\n\n')
            // reduce left indents
                    .replaceAll(' {2,}', '  ')
            // indent all not indented lines
                    .replaceAll('\n([^ ])', '\n  $1').trim()
        }
        return desc
    }

    private Map<String, String> buildCategories(Node result) {
        Map<String, String> cat = [:]
        result.BugCategory.each { category ->
            cat[category.@category] = category.Description.text()
        }
        return cat
    }
}
