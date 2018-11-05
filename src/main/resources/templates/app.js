/*******************************************************************************
    This file is part of miRTrace.

    COPYRIGHT: Marc Friedländer <marc.friedlander@scilifelab.se>, 2018
    AUTHOR: Yrin Eldfjell <yete@kth.se>

    miRTrace is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License.

    miRTrace is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program, see the LICENSES file.
    If not, see <https://www.gnu.org/licenses/>.
*******************************************************************************/
(function() {
    var qcReportResults = qcReport.results;

    var reportElements = d3.selectAll(".report")[0];
    var currentReportElement = 0;

    /* Graphics constants. */
    var SAMPLE_PADDING_X = 1;
    var MIN_SAMPLE_WIDTH = 11 + SAMPLE_PADDING_X;
    var MAX_SAMPLE_WIDTH = 35;
    var NICE_PLOT_MARGINS_X = 110;
    var LEGEND_CAPTION_OFFSET_Y = -8;
    var Y_AXIS_LABEL_X_OFFSET = 42;
    var EXTRA_Y_AXIS_LABEL_X_OFFSET_FOR_COMPLEXITY = 20;
    var TARGET_PLOT_HEIGHT_NORMAL = 307;
    var TARGET_PLOT_HEIGHT_COMPRESSED = 80;
    var MIN_SVG_WIDTH = 525;
    var COMPLEXITY_PLOT_MAX_SCALE_FACTOR = 0.7;
    var LEGEND_X_MARGIN = 30;
    var QC_FLAGS_OFFSET = 10;
    var MARGINS_FULL = { top: 62, right: 25, bottom: 25, left: 70 };
    var MARGINS_COMPRESSED = { top: 5, right: 25, bottom: 5, left: 70 };

    /* "Global" variables. */
    var clickedStateVector = new Array();
    for (var i=0; i < qcReportResults.length; i++) {
        clickedStateVector[i] = false;
    }
    var sample_width;
    var compressedMode = false;
    var margins = JSON.parse(JSON.stringify(MARGINS_FULL));
    var allReports = [];

    /* Functions. */
    var anyColIsClicked = function(clickedStateVector) {
        var anyColIsClicked = false;
        for (var i=0; i < qcReportResults.length; i++) {
            if (clickedStateVector[i]) {
                anyColIsClicked = true;
            }
        }
        return anyColIsClicked;
    }

    /* applyClickedStateVector - func that updates the DOM based on the clicked state vector. */
    var applyClickedStateVector = function(clickedStateVector) {
        var activeSelection = anyColIsClicked(clickedStateVector);
        /* Update CSS classes. */
        d3.selectAll(".plotSVG").each(function(d, j) {
            d3.select(this).selectAll(".qcBar").each(function(d, i) {
                var col = d3.select(this);
                col.classed("selectedSample", clickedStateVector[i]);
                col.classed("notSelectedSample", activeSelection && !clickedStateVector[i]);
            });
            d3.select(this).selectAll(".sample").each(function(d, i) {
                var col = d3.select(this);
                col.classed("selectedSample", clickedStateVector[i]);
                col.classed("notSelectedSample", activeSelection && !clickedStateVector[i]);
            });
            d3.select(this).selectAll(".sampleColumnBackground").each(function(d, i) {
                var col = d3.select(this);
                col.classed("selectedSample", clickedStateVector[i]);
                col.classed("notSelectedSample", activeSelection && !clickedStateVector[i]);
            });
            d3.select(this).selectAll(".x.axis").selectAll(".tick").each(function(d, i) {
                var col = d3.select(this);
                col.classed("selectedSample", clickedStateVector[i]);
                col.classed("notSelectedSample", activeSelection && !clickedStateVector[i]);
            });
        });
        d3.select("#sampleTable").selectAll(".sample").each(function(d, i) {
            var row = d3.select(this);
            row.classed("selectedSample", clickedStateVector[i]);
            row.classed("notSelectedSample", activeSelection && !clickedStateVector[i]);
        });

        /* Update legends. */
        for (var i=0; i < allReports.length; i++) {
            if (allReports[i].updateLegend !== undefined) {
                allReports[i].updateLegend();
            }
        }
    };

    var createPerSampleEventListeners = function(element) {
        element.selectAll(".sample").style("cursor", "pointer")
        element.selectAll(".sample").each(function(d, i) {
            var t = d3.select(this);
            t.on("click", function() {
                if (d3.event.ctrlKey || d3.event.metaKey) {
                    if (clickedStateVector[i] === false) {
                        clickedStateVector[i] = true;
                    } else {
                        clickedStateVector[i] = false;
                    }
                } else {
                    for (var j=0; j < qcReportResults.length; j++) {
                        clickedStateVector[j] = false;
                    }
                    clickedStateVector[i] = true;
                }
                applyClickedStateVector(clickedStateVector);
                if (!(d3.event.ctrlKey || d3.event.metaKey)) {
                    d3.selectAll(".sample").style("cursor", "pointer");
                }
                d3.event.stopPropagation();
            });
        });
    }

    /* Functions and variables for determining plot and column width. */
    var sampleColumnWidth = function() {
        var sampleWidth;
        var documentWidth = window.innerWidth
            || document.documentElement.clientWidth
            || document.body.clientWidth;

        if (qcReportResults.length * (MAX_SAMPLE_WIDTH + SAMPLE_PADDING_X) + NICE_PLOT_MARGINS_X 
            <= documentWidth) {
            sampleWidth = MAX_SAMPLE_WIDTH;
        } else {
            sampleWidth = (documentWidth - NICE_PLOT_MARGINS_X) / qcReportResults.length;
            if (sampleWidth < MIN_SAMPLE_WIDTH) {
                sampleWidth = MIN_SAMPLE_WIDTH;
            }
        }
        sampleWidth = Math.floor(sampleWidth);
        return sampleWidth;
    };
    var sample_width = sampleColumnWidth();

    var plotWidth = function(sampleWidth) { 
        return sampleWidth * qcReportResults.length;
    };
    var PLOT_WIDTH = plotWidth(sample_width);

    var svgWidth = function(plotWidth) { 
        var svgWidth = plotWidth + margins.right + margins.left;
        if (svgWidth < MIN_SVG_WIDTH) {
            svgWidth = MIN_SVG_WIDTH;
        }
        return svgWidth;
    };
    var SVG_WIDTH = svgWidth(PLOT_WIDTH);
    var rerenderSampleColumns = function() {
        if (sampleColumnWidth() === sample_width) {
            return;
        }
        var getTranslation = function() {
            /* Based on https://stackoverflow.com/a/38230545 */
            var g = document.createElementNS("http://www.w3.org/2000/svg", "g");
            g.setAttributeNS(null, "transform", transform);
            var matrix = g.transform.baseVal.consolidate().matrix;
            return [matrix.e, matrix.f];
        }
        sample_width = sampleColumnWidth();
        PLOT_WIDTH = plotWidth(sample_width);
        SVG_WIDTH = svgWidth(PLOT_WIDTH);
        var rerenderPlot = function(plotSVG) {
            d3.select(plotSVG).selectAll(".qcBar").each(function(d, i) {
                var oldTranslation = getTranslation(d3.select(this).attr("transform"));
                var newTranslation = "translate(" + 
                        (i * sample_width + (SAMPLE_PADDING_X) + margins.left + sample_width/2) + 
                        "," + (oldTranslation[1]) + ")";
                d3.select(this).attr("transform", newTranslation);
            });
            d3.select(plotSVG).selectAll(".sample").each(function(d, i) {
                d3.selectAll(this.childNodes).each(function(e, j) {
                    var newX = i * sample_width + SAMPLE_PADDING_X + margins.left;
                    d3.select(this).attr("width", sample_width - SAMPLE_PADDING_X);
                    d3.select(this).attr("x", newX);
                });
            });
            d3.select(plotSVG).selectAll(".sampleColumnBackground").each(function(d, i) {
                var newX = i * sample_width + SAMPLE_PADDING_X + margins.left;
                d3.select(this).attr("width", sample_width - SAMPLE_PADDING_X);
                d3.select(this).attr("x", newX);
            });
            d3.select(plotSVG).selectAll(".x.axis").selectAll(".tick").each(function(d, i) {
                var newX = i * sample_width + SAMPLE_PADDING_X + margins.left;
                var xScale = d3.scale.ordinal()
                    .rangeBands([0, PLOT_WIDTH])
                    .domain(d3.range(qcReportResults.length));
                d3.select(this).attr("transform", "translate(" + ((sample_width / 2) + xScale(i)) + ", 0)");
            });

        };
        d3.selectAll(".plotSVG").each(function(d, i) {
            d3.select(this).attr("width", SVG_WIDTH);
            rerenderPlot(this);
        });
    }
    
    var samplePlural = (qcReportResults.length == 1) ? "" : "s";
    if (qcReport.mirtraceMode === "qc") {
        d3.select("title").text(
                "miRTrace - " + qcReport.reportTitle + " (" + 
                qcReport.reportGenerationDateTime + ") - " + qcReportResults.length + 
                " sample" + samplePlural + ", " + qcReport.speciesVerbosename
        );
    } else {
        d3.select("title").text(
                "miRTrace - " + qcReport.reportTitle + " (" + 
                qcReport.reportGenerationDateTime + ") - " + qcReportResults.length + 
                " sample" + samplePlural
        );
    }
    d3.select("#header").select(".reportTitle").text(qcReport.reportTitle);

    /*************************************************************************
     *            ADD R_RNA SUBUNIT INFO TO RNA TYPE PLOT CAPTION             *
     *************************************************************************/
    var rref = qcReport.rRNASubunits;
    if (rref === "") {
        var rrefText = "(None)";
    } else {
        var rrefText = rref;
    }
    var t = d3.select("#rnaTypeReport").select(".plotCaption")
        .append("div");
    t.attr("class", "rna-subunits-caption");
    t.text("Reference seqs are available for these rRNA subunits: ");
    var s = t.append("span")
        .text(rrefText);
    if (rref === "") {
        s.attr("class", "rna-subunits none");
    } else {
        s.attr("class", "rna-subunits");
    }

    /*************************************************************************
     *                            RENDER HEADER                              *
     *************************************************************************/
    if (qcReport.reportComments.length > 0) {
        d3.select("#header").selectAll("div.commentField")
            .data(qcReport.reportComments)
          .enter().append("div")
            .attr("class", "commentField")
            .append("span")
              .attr("class", "comment")
              .text(function(d) { return d; });
        d3.select("#header").selectAll("div.commentField").insert("svg", ":first-child")
            .attr("viewBox", "0 0 22 28")
            .attr("class", "icon")
            .append("path")
            .attr("d", "m 25,11 c 0,-5.13615 -5.81809,-9.28571 -13,-9.28571 -7.18192,0 -13,4.16406 -13,9.28571 0,2.94531 1.91518,5.57143 4.90401,7.26897 -0.68191,2.42299 -1.72656,3.29353 -2.64062,4.32366 -0.21763,0.26117 -0.46429,0.4788 -0.39174,0.84152 0,0 0,0 0,0.0145 0.0725,0.34822 0.37723,0.58036 0.69643,0.55134 0.56584,-0.0725 1.13169,-0.17411 1.65402,-0.3192 2.51004,-0.63839 4.77343,-1.82812 6.6741,-3.51116 0.68192,0.0726 1.39286,0.11608 2.1038,0.11608 7.18191,0 13,-4.14956 13,-9.28572")
            .attr("class", "commentIconPath");
    }

    if (qcReport.warnings.length > 0) {
        d3.select("#header").selectAll("div.warningField")
            .data(qcReport.warnings)
          .enter().append("div")
            .attr("class", "warningField")
            .append("span")
              .attr("class", "warning")
              .text(function(d) { return d; });
        d3.select("#header").selectAll("div.warningField").insert("svg", ":first-child")
            .attr("viewBox", "0 0 22 32")
            .attr("class", "icon")
            .append("path")
            .attr("d", "m 14,21 c 0,0.26116 -0.20313,0.47879 -0.46429,0.47879 l -2.78571,0 c -0.26116,0 -0.46429,-0.21763 -0.46429,-0.47879 l 0,-2.7567 c 0,-0.26116 0.20313,-0.47879 0.46429,-0.47879 l 2.78571,0 c 0.26116,0 0.46429,0.21763 0.46429,0.47879 l 0,2.7567 m -0.029,-5.42634 c -0.0145,0.18861 -0.23214,0.3337 -0.4933,0.3337 l -2.68415,0 c -0.27567,0 -0.49331,-0.14509 -0.49331,-0.3337 l -0.24665,-6.63058 c 0,-0.0871 0.0435,-0.23214 0.14509,-0.30469 0.087,-0.0725 0.21763,-0.1596 0.34821,-0.1596 l 3.19197,0 c 0.13058,0 0.26116,0.0871 0.34821,0.1596 0.10157,0.0726 0.14509,0.18862 0.14509,0.27567 l -0.26116,6.6596 m -0.20312,-13.55134 c -0.3192,-0.59487 -0.94308,-0.9721 -1.625,-0.9721 -0.68192,0 -1.30581,0.37723 -1.625,0.9721 l -11.14286,20.42857 c -0.3192,0.56585 -0.30469,1.26228 0.029,1.82813 0.3337,0.56584 0.94308,0.91406 1.59598,0.91406 l 22.28571,0 c 0.65291,0 1.26228,-0.34822 1.59599,-0.91406 0.3337,-0.56585 0.34821,-1.26228 0.029,-1.82813 l -11.14285,-20.42857")
            .attr("class", "warningIconPath");
    }

    var addHeaderField = function(caption, entries) {
        var div = d3.select(".headerFields").append("div")
            .attr("class", "headerField");
        div.append("span")
            .attr("class", "caption")
            .append("span")
            .text(caption);
        var entrySpan = div.append("span")
            .attr("class", "entry");
        for (var i=0; i < entries.length; i++) {
            entrySpan.append("span").attr("class", "inner")
                .text(entries[i]);
        }
    };

    addHeaderField("Report generated:", [qcReport.reportGenerationDateTime]);
    if (qcReport.mirtraceMode === "qc") {
        addHeaderField("Species:", [qcReport.speciesVerbosename + " (" + qcReport.species + ")"]);
    }
    addHeaderField("Sample count:", [qcReport.results.length]);

    /*************************************************************************
     *                            RENDER FOOTER                              *
     *************************************************************************/
    d3.select("#mirtraceVersion").text("Generated by miRTrace (version " + 
            qcReport.mirtraceVersion + ")");

    /*************************************************************************
     *                      RENDER SAMPLE STATISTCS TABLE                    *
     *************************************************************************/
    var renderSampleStatsTable = function() {
        var firstRow = d3.select("#sampleTable").append("tr");
        firstRow.append("th").text("Sample");
        firstRow.append("th").text("Filename");
        firstRow.append("th").text("Total reads");
        firstRow.append("th").text("QC-passed reads");
        firstRow.append("th").text("miRNA reads");
        firstRow.append("th").text("Adapter");
        
        var adapterArray = [];
        for (var i=0; i < qcReportResults.length; i++) {
            if (adapterArray.indexOf(qcReportResults[i].adapter) === -1) {
                adapterArray.push(qcReportResults[i].adapter);
            }
        }
        var adapterColor = d3.scale.category10();

        var trs = d3.select("#sampleTable").selectAll("tr.sample")
          .data(qcReportResults)
            .enter().append("tr").attr("class", "sample");
        trs.append("td").text(function(d, i) {
            return d.verbosename;
        });
        trs.append("td").text(function(d, i) {
            return d.filename;
        });
        trs.append("td").text(function(d, i) {
            return d.stats.allSeqsCount.toLocaleString();
        }).attr("class", "numCol");
        trs.append("td").text(function(d, i) {
            return (d.stats.statsQC[3] + d.stats.statsQC[4]).toLocaleString();
        }).attr("class", "numCol");
        trs.append("td").text(function(d, i) {
            return d.stats.statsRNAType[0].toLocaleString();
        }).attr("class", "numCol");
        trs.append("td").text(function(d, i) {
            return d.adapter === "" ? "(none)" : d.adapter;
        }).style("color", function(d, i) {
            var adapterBin = adapterArray.indexOf(d.adapter) % 20;
            return adapterColor(adapterBin);
        }).attr("class", function(d, i) {
            return d.adapter === "" ? "" : "adapter";
        });
        createPerSampleEventListeners(d3.select("#sampleTable"));
    }
    /*************************************************************************
     *                          ADD DOWNLOAD ICONS                           *
     *************************************************************************/

    var downloadableReports = d3.selectAll("#phredReport,#lengthReport,#qcStatsReport,#rnaTypeReport,#complexityReport,#contaminationReport");
    downloadableReports.insert("a", ":first-child")
        .attr("title", "Download report (PNG)")
        .attr("class", "downloadPNGIcon")
        .attr("href", "javascript:void(0);");

    // Removed SVG download feature due to poor application support for 
    // SVG:s with embedded CSS, thus making them sort-of-useless to end users.
    /* downloadableReports.insert("a", ":first-child")
        .attr("title", "Download report (SVG)")
        .attr("class", "downloadSVGIcon")
        .attr("href", "javascript:void(0);");
    */

    /*************************************************************************
     *                            REPORT RENDERER                            *
     *************************************************************************/
    var renderReport = function(report, targetDOMContainer) {
        var r = report;
        allReports.push(report);
        if (compressedMode) {
            report.target_plot_height = TARGET_PLOT_HEIGHT_COMPRESSED;
            margins = JSON.parse(JSON.stringify(MARGINS_COMPRESSED));
        } else {
            report.target_plot_height = TARGET_PLOT_HEIGHT_NORMAL;
            margins = JSON.parse(JSON.stringify(MARGINS_FULL));
        }
        report.compressedMode = compressedMode;

        /* Set default values. */
        if (r.yAxisVerticalOffset === undefined) {
            r.yAxisVerticalOffset = 0;
        }

        r.hasWarningIcons = false;
        for (var i=0; i < r.data.length; i++) {
            var x = r.data[i].stats.qcAnalysisFlags[r.id];
            if ((x === "unknown") || (x === "ok")) {
                /* Non-rendered states. */
            } else {
                r.hasWarningIcons = true;
                break;
            }
        }
        if (r.hasWarningIcons) {
            margins.top += QC_FLAGS_OFFSET;
        }

        /* Create chart. */
        var chart = targetDOMContainer.append("span").append("svg");
        chart.attr("class", "plotSVG")
          .attr("version", 1.1)
          .attr("xmlns", "http://www.w3.org/2000/svg");
        chart.append("defs")
            .append("style")
            .attr("type", "text/css")
            .html(d3.select("#reportSVGCSS").html());

        r.chart = chart;

        /* Create x-axis. */
        var xAxisSamples = d3.svg.axis()
            .scale(r.xScale)
            .orient("bottom")
            .tickSize(8, 6)
            .tickFormat(function(d) {
                return qcReportResults[d].verbosename;
            });
        var xAxisReadCounts = d3.svg.axis()
            .scale(r.xScale)
            .orient("top")
            .tickPadding(0)
            .tickSize(0, 0)
            .tickFormat(function(d) {
                if (r.id === "contamination") {
                    var c = d3.sum(qcReportResults[d].stats.statsClades);
                } else if (r.id === "rnatype") {
                    var c = d3.sum(qcReportResults[d].stats.statsRNAType);
                } else {
                    var c = qcReportResults[d].stats.allSeqsCount;
                }
                if (c < 1000) {
                    return d3.format("3d")(c);
                } else {
                    return d3.format(".3s")(c);
                }
            });
        if (!compressedMode || report.id === "contamination") {
            chart.append("g")
                .attr("class", "x axis")
                .attr("transform", "translate(" + (margins.left) + ", " + (margins.top + r.plotHeight) + ")")
                .call(xAxisSamples)
                .selectAll("text")
                    .style("text-anchor", "end")
                    .attr("dy", ".5em") /* .5em because the font-size is 1em. */
                    .attr("transform", "rotate(-90, 0, 12)");
        }
        /* Plot read counts. */
        if (!compressedMode) {
            chart.append("g")
                .attr("class", "x axis")
                .attr("transform", "translate(" + (margins.left - 1) + ", " + 
                    (margins.top) + ")")
                .call(xAxisReadCounts)
                .selectAll("text")
                    .attr("dy", ".5em")  /* .5em because the font-size is 1em. */
                    .attr("dx", function(d, i) {
                        if (r.hasWarningIcons) {
                            return "1.4em";
                        } else {
                            return ".4em";
                        }
                    })
                    .style("text-anchor", "start")
                    .attr("transform", "rotate(-90, 0, 0)");
        }

        /* Create and configure y-axis. */
        var yAxis = d3.svg.axis()
            .scale(r.yScale)
            .orient("left");
        var yAxisLabelOffset = Y_AXIS_LABEL_X_OFFSET;
        if (r.id == "rnatype") {
            yAxis = yAxis.tickFormat(d3.format("s"))
                .tickValues([0, .1, .2, .3, .4, .5, .6, .7, .8, .9, 1]);
            yAxis = yAxis.tickFormat(function(d) {
                        return (d < .1) ? d3.format(".0%")(d) : d3.format(".0f")(d * 100);
                    });
        } else if (r.id == "qc") {
            yAxis = yAxis.tickFormat(d3.format("s"))
                .tickValues([0, .1, .2, .3, .4, .5, .6, .7, .8, .9, 1]);
            yAxis = yAxis.tickFormat(function(d) {
                        return (d < .1) ? d3.format(".0%")(d) : d3.format(".0f")(d * 100);
                    });
        } else if (r.id == "contamination") {
            yAxis = yAxis.tickFormat(d3.format("s"))
                .tickValues([0, .1, .2, .3, .4, .5, .6, .7, .8, .9, 1]);
            yAxis = yAxis.tickFormat(function(d) {
                        return (d < .1) ? d3.format(".0%")(d) : d3.format(".0f")(d * 100);
                    });
        } else if (r.id == "complexity") {
            yAxis = yAxis.tickFormat(function(d) {
                if (d < 1000) {
                    return d3.format("3d")(d);
                } else {
                    return d3.format(".2s")(d);
                }
            });
            yAxisLabelOffset += EXTRA_Y_AXIS_LABEL_X_OFFSET_FOR_COMPLEXITY;
        } else if (r.id == "phred") {
            yAxis = yAxis.tickFormat(function(d) {
                    if (d === 42) {
                        return "\u226542"; /* Greater-than symbol followed by the number 42. */
                    } else { 
                        return d3.format("s")(d);
                    }
                })
                .tickValues([0, 5, 10, 15, 20, 25, 30, 35, 40, 42]);
        } else if (r.id == "length") {
            yAxis = yAxis.tickFormat(d3.format("s"))
                .tickValues([0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50])
                .tickFormat(function(d) {
                    if (d === 50) {
                        return "\u226550"; /* Greater-than symbol followed by the number 50. */
                    } else {
                        return d3.format("s")(d);
                    }
                });
        }
        if (!compressedMode) {
            chart.append("g")
                .attr("class", "y axis")
                .attr("transform", "translate(" + (margins.left - 2) + ", " + r.yAxisVerticalOffset + ")")
                .call(yAxis);
            chart.append("g")
                .attr("transform", "translate(" + (margins.left - yAxisLabelOffset) + ", " + 
                        (margins.top + (r.plotHeight / 2)) + ")")
              .append("text")
                .attr("transform", "rotate(-90)")
                .attr("dy", ".71em")
                .attr("class", "yAxisLabel")
                .text(r.yAxisLabel);
        }

        /* Calculate space needed for tick labels. */
        var maxTextLength = 0;
        chart.selectAll(".x.axis").selectAll("text").each(function(d, i) {
            if (this.getComputedTextLength() > maxTextLength) {
                maxTextLength = this.getComputedTextLength();
            }
        });
        maxTextLength += 10; /* An attempt to compensate for the x-axis ticks. */
        r.legendYOffset = maxTextLength + 40;

        /* Set SVG dimensions. */
        if (compressedMode) {
            var compressedHeight = margins.top + r.plotHeight + 3;
            if (report.id === "contamination") {
                compressedHeight += maxTextLength;
            }
            chart.attr("height", compressedHeight).attr("width", SVG_WIDTH);
        } else {
            chart.attr("height", r.svgHeight).attr("width", SVG_WIDTH);
        }

        /* Plot QC flags bar. */
        var qcFlagPathData = "m 0,-5 c -2.83928,0 -5.14286,2.30358 -5.14286,5.14286 0,2.83928 2.30358,5.14286 5.14286,5.14286 2.83928,0 5.14286,-2.30358 5.14286,-5.14286 0,-2.83928 -2.30358,-5.14286 -5.14286,-5.14286 m 0.85714,8.35045 c 0,0.12053 -0.0937,0.22098 -0.20759,0.22098 l -1.28571,0 c -0.12054,0 -0.22098,-0.10045 -0.22098,-0.22098 l 0,-1.27232 c 0,-0.12054 0.10044,-0.22099 0.22098,-0.22099 l 1.28571,0 c 0.11384,0 0.20759,0.10045 0.20759,0.22099 l 0,1.27232 m -0.0134,-2.30357 c -0.007,0.0937 -0.10714,0.16741 -0.22768,0.16741 l -1.23884,0 c -0.12723,0 -0.22768,-0.0737 -0.22768,-0.16741 l -0.11384,-4.15849 c 0,-0.0469 0.0201,-0.0937 0.067,-0.12053 0.0402,-0.0335 0.10044,-0.0536 0.16071,-0.0536 l 1.47322,0 c 0.0603,0 0.12053,0.0201 0.16071,0.0536 0.0469,0.0268 0.067,0.0737 0.067,0.12053 l -0.12053,4.15849";
        var qcFlagPathDataWhite1 = "m 179.67923,229.39881 c -1.26829,-1.26828 -1.57341,-2.22464 -1.58275,-4.96085 -0.006,-1.86433 -0.57725,-23.12109 -1.26866,-47.23726 -0.6914,-24.11617 -1.26229,-47.48709 -1.26865,-51.93538 -0.0113,-7.89567 0.0259,-8.11235 1.56619,-9.12158 1.37211,-0.89904 4.33942,-1.03378 22.76601,-1.03378 13.21787,0 21.64849,0.24631 22.41173,0.65478 0.6729,0.36013 1.51147,1.19292 1.86348,1.85065 0.4328,0.80871 0.12803,18.47113 -0.94137,54.55477 -0.86975,29.3474 -1.58323,53.87607 -1.5855,54.50818 -0.002,0.63211 -0.71117,1.85632 -1.57532,2.72047 l -1.57118,1.57119 -18.6214,0 -18.6214,0 -1.57118,-1.57119 z";
        var qcFlagPathDataWhite2 = "m 179.80499,290.74244 -1.69694,-1.69694 0,-18.50502 0,-18.50502 1.57118,-1.57118 1.57118,-1.57118 18.80078,0 18.80078,0 1.55183,1.6515 1.55183,1.65151 0,18.14443 c 0,17.45403 -0.0513,18.21323 -1.34897,19.95223 l -1.34896,1.8078 -18.87789,0.1694 -18.87788,0.16941 -1.69694,-1.69694 z";

        var qcBarEntry = chart.append("g")
            .attr("class", "qc_flags_bar")
            .selectAll("qcBar").data(r.data)
            .enter().append("g")
                .attr("transform", function(d, i, j) {
                    return "translate(" + (i * sample_width + (SAMPLE_PADDING_X) + margins.left + sample_width/2) + 
                            "," + (margins.top - QC_FLAGS_OFFSET) + ")";
                })
                .attr("class", "qcBar");
            qcBarEntry.append("circle")
                .select(function(d) { 
                    var f = d.stats.qcAnalysisFlags[r.id]; 
                    /* Only show "bad" warnings. */
                    if (f === "ok" || f === "unknown")
                        return null;
                    return this;
                })
                .attr("cx", "0").attr("cy", "0").attr("r", "5")
                .attr("fill", "#fff")
                .attr("class", "qcBarEntry");
            qcBarEntry.append("path")
                .select(function(d) { 
                    var f = d.stats.qcAnalysisFlags[r.id]; 
                    /* Only show "bad" warnings. */
                    if (f === "ok" || f === "unknown")
                        return null;
                    return this;
                })
                .attr("d", qcFlagPathData)
                .attr("fill", function(d) {
                    var c = "#fff";
                    if (d.stats.qcAnalysisFlags[r.id] === "ok" || d.stats.qcAnalysisFlags[r.id] === "unknown") {
                        /* c = "#1d891d"; */
                        /* Don't show OK flags. */
                    } else if (d.stats.qcAnalysisFlags[r.id] === "warning") {
                        /* c = "#ff9000"; */
                        /* Warnings disabled for now, only errors (bad) shown. */
                    } else { //if (d.stats.qcAnalysisFlags[r.id] === "bad") {
                        c = "#cd1c1c";
                    }
                    return c;
                })
                .attr("class", "qcBarEntry");
            qcBarEntry.selectAll(".qcBarEntry")
                .on("click", function(d) {
                    d3.event.stopPropagation();
                    var clickedIndex = qcReportResults.indexOf(d);
                    var sampleVerbosename = d.verbosename;
                    if (d.verbosename.length > 12) {
                        sampleVerbosename = d.verbosename.substring(0, 10) + "...";
                    }
                    d3.selectAll(".bigWarningContainer").remove();
                    var c = "#ed7070";
                    var c2 = "#ffaaaa";
                    var ctext = "#000";
                    var bigWarningContainer = d3.select(".reportContainer")
                        .append("div").attr("class", "bigWarningContainer");
                    var containerOffset = 40;
                    if (compressedMode) {
                        containerOffset = 145;
                    }
                    bigWarningContainer.style("top", (d3.event.pageY - containerOffset) + "px");
                    var ch = bigWarningContainer.append("svg");
                    ch.attr("height", 400)
                      .attr("width", 400)
                      .attr("version", 1.1)
                      .attr("xmlns", "http://www.w3.org/2000/svg");
                    var bigWarningGroup = ch.append("g")
                        .attr("transform", function(d, i, j) {
                            return "translate(200,200)";
                        })
                        .attr("class", "bigWarningGroup");
                    bigWarningGroup.append("path").attr("d", qcFlagPathDataWhite1)
                        .attr("transform", "matrix(0.03846154,0,0,0.03846154,-7.6923077,-7.6923077)")
                        .attr("fill", c2)
                        .style("stroke", c2)
                        .style("stroke-width", "1.5")
                        .style("stroke-linecap", "round")
                        .style("stroke-linejoin", "miter")
                        .style("opacity", "0.9");
                    bigWarningGroup.append("path").attr("d", qcFlagPathDataWhite2)
                        .attr("transform", "matrix(0.03846154,0,0,0.03846154,-7.6923077,-7.6923077)")
                        .attr("fill", c2)
                        .style("stroke", c2)
                        .style("stroke-width", "1.5")
                        .style("stroke-linecap", "round")
                        .style("stroke-linejoin", "miter")
                        .style("opacity", "0.9");
                    bigWarningGroup.append("path").attr("d", qcFlagPathData)
                        .attr("fill", c)
                        .style("opacity", "0.9");
                    bigWarningGroup.append("text")
                      .attr("transform", "translate(0,-3.7)scale(.035)")
                      .style("text-anchor", "middle")
                      .attr("fill", ctext)
                      .style("text-decoration", "underline")
                      .text(function() { 
                          return sampleVerbosename;
                      });
                    bigWarningGroup.append("text")
                      .attr("transform", "translate(2.2,-3.7)scale(.035)")
                      .style("text-anchor", "middle")
                      .style("cursor", "pointer")
                      .attr("fill", ctext)
                      .text("[x]");
                    bigWarningGroup.append("text")
                      .attr("transform", "translate(0,-1)scale(.035)")
                      .style("text-anchor", "middle")
                      .attr("fill", ctext)
                      .style("font-weight", "bold")
                      .text(function() { 
                          return d.stats.qcAnalysisFlags[r.id];
                      });
                    var criteriaLines = qcReport.qcCriteriaVerbose[r.id].split(/\n/);
                    for (var i=0; i < criteriaLines.length; i++) {
                        bigWarningGroup.append("text")
                          .attr("transform", "translate(0," + (0.5 + 0.6*i) + ")scale(.035)")
                          .style("text-anchor", "middle")
                          .attr("fill", ctext)
                          .text(function() { 
                              return criteriaLines[i].replace("\n", "");
                          });
                    }
                    bigWarningGroup.on("click", function(e) {
                        d3.selectAll(".bigWarningContainer").remove();
                        d3.event.stopPropagation();
                    });
                    bigWarningGroup.transition()
                      .duration(300)
                      .attr("transform", "translate(200,200)scale(26)")
                      /*  .transition()
                      .delay(2000)
                      .duration(3000)
                      .style("opacity", "0")
                      .remove();*/
//                    bigWarningContainer.transition().delay(300 + 2000 + 3000).remove();
                });
        /* Plot content */
        r.renderSample(r);

        /* Create legend */
        var legendGroup = chart.append("g")
            .attr("class", "legendGroup")
            .attr("transform", "translate(" + LEGEND_X_MARGIN + ", " + 
                    (r.plotHeight + margins.top + r.legendYOffset) + ")");
        r.renderLegend(legendGroup, r);

        /* Create event listeners. */
        (function(r) {
            var r = r;
            createPerSampleEventListeners(r.chart);
            var saveToPNG = function() {
                var w = chart.attr("width");
                var h = chart.attr("height");
                var img = new Image();
                var serializer = new XMLSerializer();
                var svgStr = serializer.serializeToString(chart[0][0]);
                var canvas = document.createElement("canvas");
                canvas.className = "exportRenderCanvas";
                canvas.style.cssText = "display: none;";
                canvas.width = w;
                canvas.height = h;
                document.body.appendChild(canvas);
                var context = canvas.getContext("2d");
                img.onload = function() {
                    context.drawImage(img,0,0,w,h);
                    canvas.toBlob(function(blob) {
                        saveAs(blob, "mirtrace-" + r.id + "-plot.png");
                    });
                }; 
                img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svgStr);
            };
            var saveToSVG = function() {
                var svgContent = r.chart.node().parentNode.innerHTML;
                svgContent = svgContent.replace(/<style.*?>/g, function(text) {
                    return text + "\n/* <![CDATA[ */";
                });
                svgContent = svgContent.replace("</style>", "/* ]]> */\n</style>");
                var blob = new Blob([svgContent], {type: "image/svg+xml"});
                saveAs(blob, "mirtrace-" + r.id + "-plot.svg");
            };
            try {
                var fileSaverIsSupported = !!new Blob;
                if (fileSaverIsSupported) {
                    targetDOMContainer.selectAll(".downloadPNGIcon").on("click", saveToPNG);
                    targetDOMContainer.selectAll(".downloadSVGIcon").on("click", saveToSVG);
                } else {
                    targetDOMContainer.selectAll(".downloadPNGIcon").on("click", 
                        alert("Download not supported on this platform."));
                    targetDOMContainer.selectAll(".downloadSVGIcon").on("click", 
                        alert("Download not supported on this platform."));
                }
            } catch (e) {}

        })(r);
    };


    /*************************************************************************
     *                             EVENT LISTENERS                           *
     *************************************************************************/
    d3.select("#compressReportsButton").on("click", function(e) {
        compressedMode = !compressedMode;
        d3.selectAll(".plotSVG").remove();
        if (compressedMode) {
            var display = "none";
            var fontSize = "1em";
            d3.select(this).attr("value", "Expand plot height");
        } else {
            var display = "block";
            var fontSize = "1.3em";
            d3.select(this).attr("value", "Compress plot height");
        }
        d3.selectAll(".plotCaption").style("display", display);
        d3.selectAll(".warningField").style("display", display);

        d3.select("#phredReport > .reportTitle").style("font-size", fontSize);
        d3.select("#lengthReport > .reportTitle").style("font-size", fontSize);
        d3.select("#qcStatsReport > .reportTitle").style("font-size", fontSize);
        d3.select("#rnaTypeReport > .reportTitle").style("font-size", fontSize);
        d3.select("#complexityReport > .reportTitle").style("font-size", fontSize);
        d3.select("#contaminationReport > .reportTitle").style("font-size", fontSize);
        renderReports();
    });

    /* Global event listeners. */
    d3.select(window).on("click", function() {
        if (!(d3.event.ctrlKey || d3.event.metaKey)) {
            d3.selectAll(".sample").style("cursor", "pointer");
            for (var i=0; i < qcReportResults.length; i++) {
                clickedStateVector[i] = false;
            }
            applyClickedStateVector(clickedStateVector);
            d3.selectAll(".bigWarningContainer").remove();
            d3.selectAll(".sample").style("cursor", "pointer");
        }
    }).on("keydown", function() {
        var ESC_KEY = 27;
        var LEFT_KEY = 37;
        var RIGHT_KEY = 39;
        var UP_KEY = 38;
        var DOWN_KEY = 40;
        var DIVISOR_ELEMENT_PADDING = 2;
        var TOP_MENU_HEIGHT = d3.select("#topMenu").node().getBoundingClientRect().height;
        var activeSelection = anyColIsClicked(clickedStateVector);

        if (d3.event.ctrlKey || d3.event.metaKey) {
            d3.selectAll(".sample").style("cursor", "cell");
        }
        if (d3.event.ctrlKey || d3.event.shiftKey || d3.event.altKey || d3.event.metaKey) {
            return;
        }
        if (d3.event.keyCode === ESC_KEY) {
            for (var i=0; i < qcReportResults.length; i++) {
                clickedStateVector[i] = false;
            }
            applyClickedStateVector(clickedStateVector);
        }
        if (d3.event.keyCode === 'A'.charCodeAt(0) || 
                d3.event.keyCode === 'H'.charCodeAt(0) ||
                d3.event.keyCode === LEFT_KEY) {
            if (activeSelection) {
                /* Rotate clickedStateVector left. */
                var tmp = clickedStateVector[0];
                for (var i=0; i < qcReportResults.length - 1; i++) {
                    clickedStateVector[i] = clickedStateVector[i+1];
                }
                clickedStateVector[qcReportResults.length-1] = tmp;
                applyClickedStateVector(clickedStateVector);
            } else {
                /* Scroll window to the left. */
                window.scrollBy(-100, 0);
            }
        } else if (d3.event.keyCode === 'D'.charCodeAt(0) || 
                d3.event.keyCode === 'L'.charCodeAt(0) ||
                d3.event.keyCode === RIGHT_KEY) {
            if (activeSelection) {
                /* Rotate clickedStateVector right. */
                var tmp = clickedStateVector[qcReportResults.length-1];
                for (var i=qcReportResults.length - 1; i >= 1 ;i--) {
                    clickedStateVector[i] = clickedStateVector[i-1];
                }
                clickedStateVector[0] = tmp;
                applyClickedStateVector(clickedStateVector);
            } else {
                /* Scroll window to the right. */
                window.scrollBy(100, 0);
            }
        } else if (d3.event.keyCode === 'W'.charCodeAt(0) || 
                d3.event.keyCode === 'K'.charCodeAt(0) ||
                d3.event.keyCode === UP_KEY) {
            currentReportElement--;
            if (currentReportElement < 0) {
                currentReportElement = 0;
            }
            reportElements[currentReportElement].scrollIntoView();
            window.scrollBy(0, -TOP_MENU_HEIGHT + DIVISOR_ELEMENT_PADDING);
        } else if (d3.event.keyCode === 'S'.charCodeAt(0) || 
                d3.event.keyCode === 'J'.charCodeAt(0) ||
                d3.event.keyCode === DOWN_KEY) {
            currentReportElement++;
            if (currentReportElement >= reportElements.length) {
                currentReportElement = reportElements.length - 1;
            }
            reportElements[currentReportElement].scrollIntoView();
            window.scrollBy(0, -TOP_MENU_HEIGHT + DIVISOR_ELEMENT_PADDING);
        }
        if (d3.event.keyCode === DOWN_KEY || d3.event.keyCode === UP_KEY ||
                d3.event.keyCode === LEFT_KEY || d3.event.keyCode === RIGHT_KEY) {
            d3.event.preventDefault();
        }
    })
    .on("keyup", function() {
        if (!(d3.event.ctrlKey || d3.event.metaKey)) {
            d3.selectAll(".sample").style("cursor", "pointer");
        }
    });


    /*************************************************************************
     *                               PHRED REPORT                            *
     *************************************************************************/
    var phredReport = {
        id: "phred",
        data: qcReportResults,
        legendYOffset: 60,
        legendHeight: 10,
        get svgHeight() {
            return (this.plotHeight + margins.top + margins.bottom + 
                    this.legendYOffset + this.legendHeight);
        },
        get xScale() { 
            return d3.scale.ordinal()
                .rangeBands([0, PLOT_WIDTH])
                .domain(this.data.map(function(d, i, j) { 
                    return i;
                }))
        },
        /* 
         * Report specific settings. 
         */
        get boxHeight() { 
            var goal_height = (this.target_plot_height - 1) / (this.maxPhredScore + 1);
            return Math.round(goal_height);
        },
        maxPhredScore: 42,
        get plotHeight() {
            return (this.boxHeight * (this.maxPhredScore + 1)) + 1
        },
        get yScale() {
            return d3.scale.linear()
                .domain([qcReport.minPhredScore, this.maxPhredScore])
                /* We're removing 1 boxHeight so that the ticks line up to the middle of the boxes. */
                .range([this.plotHeight - this.boxHeight + margins.top, margins.top]);

        },
        yAxisLabel: "PHRED score",
        get colorScale() {
            return d3.scale.threshold()
                .domain([.001, .01, .05, .10, .25, .40])
                .range(colorbrewer.Reds[7]);
        },
        get legendScale() {
            var legendWidth = SVG_WIDTH - margins.right - margins.left;
            var MAX_LEGEND_WIDTH = 380;
            if (legendWidth > MAX_LEGEND_WIDTH) {
                legendWidth = MAX_LEGEND_WIDTH;
            }
            var w = legendWidth / 7;
            return d3.scale.linear()
                .domain([0, .001, .01, .05, .10, .25, .40, .50])
                .range([0, w, 2*w, 3*w, 4*w, 5*w, 6*w, 7*w]);
        },
        get legendAxis() {
            var csDomain = this.colorScale.domain();
            return d3.svg.axis()
                .scale(this.legendScale)
                .orient("bottom")
                .tickSize(13)
                .tickValues(csDomain)
                .tickFormat(function(d, i) {
                    if (d === d3.min(csDomain)) {
                        return d3.format(".1%")(d);
                    } else if (d === d3.max(csDomain)) {
                        return d3.format(".0%")(d); 
                    } else {
                        return d3.format(".0f")(d * 100);
                    }
                });
        },
        get yAxisVerticalOffset() { return this.boxHeight/2; },
        statsKey: "statsNucleotidePhredScores",
        renderSample: function(report) {
            report.chart.selectAll(".sampleColumnBackground").data(report.data)
              .enter().append("rect")
                .attr("class", "sampleColumnBackground")
                .attr("x", function(d, i, j) { 
                    return i * sample_width + (SAMPLE_PADDING_X) + margins.left;
                })
                .attr("y", margins.top)
                .attr("width", sample_width - (SAMPLE_PADDING_X))
                .attr("height", report.plotHeight + 1)
                .attr("fill", "#000000");
            var cell = report.chart.selectAll(".sample").data(report.data)
              .enter().append("g")
                .attr("class", "sample")
                .selectAll("rect")
                .data(function(d) { return d["stats"][report.statsKey]; } )
                .enter();
            cell.append("rect")
                .attr("x", function(d, i, j) { 
                    return j * sample_width + SAMPLE_PADDING_X + margins.left;
                })
                .attr("y", function(d, i, j) { 
                    return report.plotHeight - ((i+1) * report.boxHeight) + margins.top;
                })
                .attr("height", report.boxHeight)
                .attr("width", sample_width - SAMPLE_PADDING_X)
                .style("fill", function(d, i, j) {
                    var frac = (d / d3.sum(report.data[j]["stats"][report.statsKey]));
                    return report.colorScale(frac);
                });
        },
        legendCaption: "Percentage of all nucleotides",
        renderLegend: function(legendGroup, report) {
            legendGroup.selectAll("rect")
                .data(report.colorScale.range().map(function(color) {
                    var frac = report.colorScale.invertExtent(color);
                    if (frac[0] == null) {
                        frac[0] = report.legendScale.domain()[0];
                    }
                    if (frac[1] == null) {
                        frac[1] = report.legendScale.domain()[report.legendScale.domain().length - 1];
                    }
                    return frac
                }))
                .enter().append("rect")
                    .attr("height", 6)
                    .attr("x", function(frac) { 
                        return report.legendScale(frac[0]); 
                    })
                    .attr("width", function(frac) { 
                        return report.legendScale(frac[1]) - report.legendScale(frac[0]); 
                    })
                    .style("fill", function(frac) { 
                        return report.colorScale(frac[0]); 
                    });
            legendGroup.call(report.legendAxis).append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y)
                .text(report.legendCaption);
        }
    };


    /*************************************************************************
     *                             LENGTH REPORT                             *
     *************************************************************************/
    var lengthReport = {
        id: "length",
        /* Default settings. 
         *
         * (Should of course be a separate object, but JavaScript is 
         * giving me nothing but pain on that topic today. :-/ )
         */
        data: qcReportResults,
        legendYOffset: 60,
        legendHeight: 10,
        get svgHeight() {
            return (this.plotHeight + margins.top + margins.bottom + 
                    this.legendYOffset + this.legendHeight);
        },
        get xScale() { 
            return d3.scale.ordinal()
                .rangeBands([0, PLOT_WIDTH])
                .domain(this.data.map(function(d, i, j) { 
                    return i;
                }))
        },
        /* 
         * Report specific settings. 
         */
        get boxHeight() { 
            var goal_height = (this.target_plot_height - 1) / (this.maxSequenceLength + 1);
            return Math.round(goal_height);
        },
        get plotHeight() {
            return (this.boxHeight * (this.maxSequenceLength + 1)) + 1;
        },
        get minSequenceLength() {
            return qcReport["minSequenceLength"];
        },
        get maxSequenceLength() {
            return qcReport["maxSequenceLength"];
        },
        get yScale() {
            return d3.scale.linear()
                .domain([0, this.maxSequenceLength])
                /* We're removing 1 boxHeight so that the ticks line up with the middle of the boxes. */
                .range([this.plotHeight - this.boxHeight + margins.top, margins.top]);
        },
        yAxisLabel: "Read length",
        get colorScale() {
            return d3.scale.threshold()
                .domain([.001, .01, .05, .10, .25, .40])
                .range(colorbrewer.YlOrBr[7]);
        },
        get legendScale() {
            var legendWidth = SVG_WIDTH - margins.right - margins.left;
            var MAX_LEGEND_WIDTH = 380;
            if (legendWidth > MAX_LEGEND_WIDTH) {
                legendWidth = MAX_LEGEND_WIDTH;
            }
            var w = legendWidth / 7;
            return d3.scale.linear()
                .domain([0, .001, .01, .05, .10, .25, .40, .50])
                .range([0, w, 2*w, 3*w, 4*w, 5*w, 6*w, 7*w]);
        },
        get legendAxis() {
            var csDomain = this.colorScale.domain();
            return d3.svg.axis()
                .scale(this.legendScale)
                .orient("bottom")
                .tickSize(13)
                .tickValues(csDomain)
                .tickFormat(function(d, i) {
                    if (d === d3.min(csDomain)) {
                        return d3.format(".1%")(d);
                    } else if (d === d3.max(csDomain)) {
                        return d3.format(".0%")(d); 
                    } else {
                        return d3.format(".0f")(d * 100);
                    }
                });
        },
        get yAxisVerticalOffset() { return this.boxHeight/2; },
        statsKey: "statsLength",
        renderSample: function(report) {
            report.chart.selectAll(".sampleColumnBackground").data(report.data)
              .enter().append("rect")
                .attr("class", "sampleColumnBackground")
                .attr("x", function(d, i, j) { 
                    return i * sample_width + (SAMPLE_PADDING_X) + margins.left;
                })
                .attr("y", margins.top)
                .attr("width", sample_width - (SAMPLE_PADDING_X))
                .attr("height", report.plotHeight + 1)
                .attr("fill", "#000000");
            var cell = report.chart.selectAll(".sample").data(report.data)
              .enter().append("g")
                .attr("class", "sample")
                .selectAll("rect")
                .select(function(d, i, j) {
                    return (i <= this.maxSequenceLength) ? this : null;
                })
                .data(function(d) { return d["stats"][report.statsKey]; } )
                .enter();
            cell.append("rect")
                .attr("x", function(d, i, j) { 
                    return j * sample_width + SAMPLE_PADDING_X + margins.left;
                })
                .attr("y", function(d, i, j) { 
                    return report.plotHeight - ((i+1) * report.boxHeight) + margins.top;
                })
                .attr("height", report.boxHeight)
                .attr("width", sample_width - SAMPLE_PADDING_X)
                .style("fill", function(d, i, j) {
                    var frac = (d / d3.sum(report.data[j]["stats"][report.statsKey]));
                    return report.colorScale(frac);
                });
        },
        legendCaption: "Percentage of all reads",
        renderLegend: function(legendGroup, report) {
            legendGroup.selectAll("rect")
                .data(report.colorScale.range().map(function(color) {
                    var frac = report.colorScale.invertExtent(color);
                    if (frac[0] == null) {
                        frac[0] = report.legendScale.domain()[0];
                    }
                    if (frac[1] == null) {
                        frac[1] = report.legendScale.domain()[report.legendScale.domain().length - 1];
                    }
                    return frac
                }))
                .enter().append("rect")
                    .attr("height", 6)
                    .attr("x", function(frac) { 
                        return report.legendScale(frac[0]); 
                    })
                    .attr("width", function(frac) { 
                        return report.legendScale(frac[1]) - report.legendScale(frac[0]); 
                    })
                    .style("fill", function(frac) { 
                        return report.colorScale(frac[0]); 
                    });
            legendGroup.call(report.legendAxis).append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y)
                .text(report.legendCaption);
        }
    };


    /*************************************************************************
     *                           QC STATUS REPORT                            *
     *************************************************************************/

    var qcStatsReport = {
        id: "qc",
        /* Default settings. */
        data: qcReportResults,
        legendYOffset: 70,
        legendHeight: 110,
        get svgHeight() {
            return (this.plotHeight + margins.top + margins.bottom + 
                    this.legendYOffset + this.legendHeight);
        },
        get xScale() { 
            return d3.scale.ordinal()
                .rangeBands([0, PLOT_WIDTH])
                .domain(this.data.map(function(d, i, j) { 
                    return i;
                }))
        },
        /* 
         * Report specific settings. 
         */
        get plotHeight() { 
            return this.target_plot_height;
        },
        get yScale() {
            return d3.scale.linear()
                .domain([0, 1])
                .range([this.plotHeight + margins.top, margins.top]);
        },
        get colorScale() {
            var greens = Array.prototype.slice.call(colorbrewer.Greens[5]);
            return d3.scale.ordinal()
                .domain([0, 1, 2, 3, 4])
                .range(greens);
        },
        yAxisLabel: "Reads (%)",
        statsKey: "statsQC",
        renderSample: function(report) {
            report.chart.selectAll(".sampleColumnBackground").data(report.data)
              .enter().append("rect")
                .attr("class", "sampleColumnBackground")
                .attr("x", function(d, i, j) { 
                    return i * sample_width + (SAMPLE_PADDING_X) + margins.left;
                })
                .attr("y", margins.top - 1)
                .attr("width", sample_width - (SAMPLE_PADDING_X))
                .attr("height", report.plotHeight + 2)
                .attr("fill", "#000000");
            var cell = report.chart.selectAll(".sample").data(report.data)
              .enter().append("g")
                .attr("class", "sample")
                .selectAll("rect")
                .data(function(d, i, j) { 
                    var arr = d["stats"][report.statsKey];
                    var cumulativeFracs = [0];
                    var res = [];
                    for (var t=1; t <= arr.length; t++) {
                        var frac = (arr[t-1] / d3.sum(arr));
                        cumulativeFracs[t] = cumulativeFracs[t-1] + frac;
                        res[t - 1] = [cumulativeFracs[t-1], cumulativeFracs[t]];
                    }
                    return res;
                })
                .enter();
            cell.append("rect")
                .attr("x", function(d, i, j) { 
                    return j * sample_width + SAMPLE_PADDING_X + margins.left;
                })
                .attr("y", function(d, i, j) {
                    return (d[0] * (report.plotHeight)) + margins.top;
                })
                .attr("height", function(d, i, j) {
                    return (d[1] - d[0])*(report.plotHeight);
                })
                .attr("width", sample_width - SAMPLE_PADDING_X)
                .style("fill", function(d, i, j) {
                    return report.colorScale(i);
                });
        },
        detectedQCTypes: function() {
            var t = this;
            var activeSelection = anyColIsClicked(clickedStateVector);
            var detectedQCTypes = {};
            this.colorScale.domain().forEach(function(d) {
                detectedQCTypes[d] = false;
                for (i=0; i < qcReportResults.length; i++) {
                    if (activeSelection && !clickedStateVector[i])
                        continue;
                    if (t.data[i].stats[t.statsKey][d] > 0) {
                        detectedQCTypes[d] = true;
                    }
                }
            });
            return detectedQCTypes;
        },
        qcTypeFractions: function() {
            var t = this;
            var activeSelection = anyColIsClicked(clickedStateVector);
            var qcFractions = [];
            var totalReadCount = 0;
            for (var i=0; i < qcReportResults.length; i++) {
                if (activeSelection && !clickedStateVector[i])
                    continue;
                var s = d3.sum(t.data[i].stats.statsQC);
                totalReadCount += s;
            }
            t.colorScale.domain().forEach(function(d) {
                qcFractions[d] = 0;
                for (var i=0; i < qcReportResults.length; i++) {
                    if (activeSelection && !clickedStateVector[i])
                        continue;
                    qcFractions[d] += (t.data[i].stats.statsQC[d] / totalReadCount);
                }
            });
            return qcFractions;
        },
        get qcStatusNames () {
            var qcStatusNames = new Array();
            qcStatusNames[0] = "Low PHRED score";
            qcStatusNames[1] = "Low complexity";
            qcStatusNames[2] = "Length < 18 nt";
            qcStatusNames[3] = "Adapter not detected";
            qcStatusNames[4] = "Adapter detected, insert \u2265 18 nt";
            return qcStatusNames;
        },
        renderLegend: function(legendGroup, report) {
            var detectedQCTypes = report.detectedQCTypes();
            var legendEntry = legendGroup.selectAll(".legend")
                .data(report.colorScale.domain())
              .enter().append("g")
                .attr("class", "legend")
                .style("opacity", function(d, i) {
                    if (detectedQCTypes[d]) {
                        return "1";
                    } else {
                        return "0.3";
                    }
                })
                .attr("transform", function(d, i) {
                    //var x = [290, 290, 290, 0, 0][d];
                    //var y = [60, 40, 20, 40, 20][d]
                    var x= 0;
                    var y = [120, 100, 80, 40, 20][d]
                    return "translate(" + x + "," + y + ")"; 
                });
            legendGroup.append("g")
                .attr("transform", function(d, i) { 
                    var x = 18;
                    var y = 0;
                    return "translate(" + x + "," + y + ")"; 
                })
                .append("text")
                .attr("x", 0)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCategoryHeader")
                .text("― Retained ―");
            legendGroup.append("g")
                .attr("transform", function(d, i) { 
                    var x = 18;
                    var y = 60; 
                    return "translate(" + x + "," + y + ")"; 
                })
                .append("text")
                .attr("x", 0)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCategoryHeader")
                .text("― Discarded ―");
            legendEntry.append("rect")
                .attr("x", 0)
                .attr("height", 14)
                .attr("width", 14)
                .style("stroke", "#000000")
                .style("stroke-width", 1)
                .style("shape-rendering", "crispEdges")
                .style("fill", report.colorScale);
            /*legendEntry.append("text")
                .attr("x", 18)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .style("text-anchor", "start");*/

            legendEntry.append("text")
                .attr("x", 18)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCol1")
                .style("text-anchor", "start");
            legendEntry.append("rect")
                .attr("x", 297)
                .attr("y", -2)
                .attr("rx", 6)
                .attr("ry", 6)
                .attr("class", "legendCol2")
                .attr("width", function(d) { return 56; })
                .attr("height", function(d) { return 18; })
                .style("fill", "#303030");
            legendEntry.append("text")
                .attr("x", 300 + 48)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCol2")
                .style("text-anchor", "end")
                .style("fill", "#fff");
            report.updateLegend();
            legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y)
                .attr("x", 18)
                .text("QC status");
            var colHeader2 = legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y)
                .attr("x", 300)
                .text("Fraction (%)")
                .attr("fill", "#000");
        },
        updateLegend: function() {
            var report = this;
            var detectedQCTypes = report.detectedQCTypes();
            var qcFractions = report.qcTypeFractions();
            report.chart.selectAll(".legend").style("opacity", function(d, i) {
                if (detectedQCTypes[d]) {
                    return "1";
                } else {
                    return "0.3";
                }
            });
            report.chart.selectAll("rect.legendCol2").style("opacity", function(d, i) {
                if (detectedQCTypes[d]) {
                    return "1";
                } else {
                    return "0";
                }
            });
            report.chart.selectAll(".legend .legendCol1").text(function(d) {
                return report.qcStatusNames[d];
            });
            report.chart.selectAll(".legend .legendCol2").text(function(d) {
                var statsText = "" + d3.format("3.1f")(qcFractions[d]*100) +  "%";
                return statsText;
            });
            
            //.attr("xml:space", "preserve");


        }
    };


    /*************************************************************************
     *                            RNA TYPE REPORT                            *
     *************************************************************************/

    var rnaTypeReport = {
        id: "rnatype",
        /* Default settings. */
        data: qcReportResults,
        legendYOffset: 65,
        legendHeight: 90,
        get svgHeight() {
            return (this.plotHeight + margins.top + margins.bottom + 
                    this.legendYOffset + this.legendHeight);
        },
        get xScale() { 
            return d3.scale.ordinal()
                .rangeBands([0, PLOT_WIDTH])
                .domain(this.data.map(function(d, i, j) { 
                    return i;
                }))
        },
        /* 
         * Report specific settings. 
         */
        get plotHeight() { 
            return this.target_plot_height;
        },
        get yScale() {
            return d3.scale.linear()
                .domain([0, 1])
                .range([this.plotHeight + margins.top, margins.top]);
        },
        get colorScale() {
            var greens = Array.prototype.slice.call(colorbrewer.Purples[5]);
            greens.reverse();

            var blues = Array.prototype.slice.call(colorbrewer.Blues[5]);
            blues.reverse();
            return d3.scale.ordinal()
                .domain([0, 1, 2, 3, 4])
                .range(blues);
        },
        yAxisLabel: "Quality filtered reads (%)",
        statsKey: "statsRNAType",
        renderSample: function(report) {
            report.chart.selectAll(".sampleColumnBackground").data(report.data)
              .enter().append("rect")
                .attr("class", "sampleColumnBackground")
                .attr("x", function(d, i, j) { 
                    return i * sample_width + (SAMPLE_PADDING_X) + margins.left;
                })
                .attr("y", margins.top - 1)
                .attr("width", sample_width - (SAMPLE_PADDING_X))
                .attr("height", report.plotHeight + 2)
                .attr("fill", "#000000");
            var cell = report.chart.selectAll(".sample").data(report.data)
              .enter().append("g")
                .attr("class", "sample")
                .selectAll("rect")
                .data(function(d, i, j) { 
                    var arr = d["stats"][report.statsKey];
                    var cumulativeFracs = [0];
                    var res = [];
                    for (var t=1; t <= arr.length; t++) {
                        var frac = (arr[t-1] / d3.sum(arr));
                        cumulativeFracs[t] = cumulativeFracs[t-1] + frac;
                        res[t - 1] = [cumulativeFracs[t-1], cumulativeFracs[t]];
                    }
                    return res;
                })
                .enter();
            cell.append("rect")
                .attr("x", function(d, i, j) { 
                    return j * sample_width + SAMPLE_PADDING_X + margins.left;
                })
                .attr("y", function(d, i, j) {
                    return (report.plotHeight) - (d[1] * report.plotHeight) + margins.top;
                })
                .attr("height", function(d, i, j) {
                    return (d[1] - d[0])*(report.plotHeight);
                })
                .attr("width", sample_width - SAMPLE_PADDING_X)
                .style("fill", function(d, i, j) {
                    return report.colorScale(i);
                });
        },
        detectedRNATypes: function() {
            /* We need this function also (not just rnaDetectionCounts), 
               because the "unknown" category never has any counts
               according to the other function. */
            var t = this;
            var activeSelection = anyColIsClicked(clickedStateVector);
            var detectedRNATypes = {};
            this.colorScale.domain().forEach(function(d) {
                detectedRNATypes[d] = false;
                for (i=0; i < qcReportResults.length; i++) {
                    if (activeSelection && !clickedStateVector[i])
                        continue;
                    if (t.data[i].stats[t.statsKey][d] > 0) {
                        detectedRNATypes[d] = true;
                    }
                }
            });
            return detectedRNATypes;
        },
        rnaDetectionCounts: function() {
            var t = this;
            var activeSelection = anyColIsClicked(clickedStateVector);
            var rnaDetectedStatus = [];
            var rnaDetectionCounts = [];
            t.colorScale.domain().forEach(function(d) {
                rnaDetectedStatus[d] = {};
                rnaDetectionCounts[d] = 0;
                for (var i=0; i < qcReportResults.length; i++) {
                    if (activeSelection && !clickedStateVector[i])
                        continue;
                    for (var j=0; j < t.data[i].stats["foundRNAReads"][d].length; j++) {
                        var seqId = t.data[i].stats["foundRNAReads"][d][j];
                        rnaDetectedStatus[d][seqId] = true;
                    }
                }
                for (var k in rnaDetectedStatus[d]) {
                    if (rnaDetectedStatus[d].hasOwnProperty(k)) {
                        if (rnaDetectedStatus[d][k]) {
                            rnaDetectionCounts[d]++;
                        }
                    }
                }
            });
            return rnaDetectionCounts;
        },
        rnaTypeFractions: function() {
            var t = this;
            var rnaTypeFractions = [];
            var activeSelection = anyColIsClicked(clickedStateVector);
            var totalReadCount = 0;
            for (var i=0; i < qcReportResults.length; i++) {
                if (activeSelection && !clickedStateVector[i])
                    continue;
                var s = d3.sum(t.data[i].stats.statsRNAType);
                totalReadCount += s;
            }
            t.colorScale.domain().forEach(function(d) {
                rnaTypeFractions[d] = 0;
                for (var i=0; i < qcReportResults.length; i++) {
                    if (activeSelection && !clickedStateVector[i])
                        continue;
                    rnaTypeFractions[d] += (t.data[i].stats.statsRNAType[d] / totalReadCount);
                }
            });
            return rnaTypeFractions;
        },
        get rnaTypeNames() {
            var rnaTypeNames = new Array();
            rnaTypeNames[0] = "miRNA";
            rnaTypeNames[1] = "rRNA";
            rnaTypeNames[2] = "tRNA";
            rnaTypeNames[3] = "Artifacts";
            rnaTypeNames[4] = "Unknown";
            return rnaTypeNames;
        },
        renderLegend: function(legendGroup, report) {
            var rnaDetectionCounts = report.rnaDetectionCounts();
            var detectedRNATypes = report.detectedRNATypes();
            var legendEntry = legendGroup.selectAll(".legend")
                .data(report.colorScale.domain())
              .enter().append("g")
                .attr("class", "legend")
                .style("opacity", function(d, i) {
                    if (detectedRNATypes[d]) {
                        return "1";
                    } else {
                        return "0.3";
                    }
                })
                .attr("transform", function(d, i) { 
                    var x = Math.floor(i / 3) * 220;
                    var y = (i % 3) * 20;
                    x = 0; 
                    y = i * 20 + 18;
                    return "translate(" + x + "," + y + ")"; 
                });
            legendEntry.append("rect")
                .attr("x", 0)
                .attr("height", 14)
                .attr("width", 14)
                .style("stroke", "#000000")
                .style("stroke-width", 1)
                .style("shape-rendering", "crispEdges")
                .style("fill", report.colorScale);
            legendEntry.append("text")
                .attr("x", 18)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCol1")
                .style("text-anchor", "start");
            legendEntry.append("rect")
                .attr("x", 107)
                .attr("y", -2)
                .attr("rx", 6)
                .attr("ry", 6)
                .attr("class", "legendCol2")
                .attr("width", function(d) { return 56; })
                .attr("height", function(d) { return 18; })
                .style("fill", "#303030");
            legendEntry.append("text")
                .attr("x", 110 + 48)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCol2")
                .style("text-anchor", "end")
                .style("fill", "#fff");
            legendEntry.append("text")
                .attr("x", 299)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCol3")
                .style("text-anchor", "end");
            legendEntry.append("text")
                .attr("x", 373)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCol4")
                .style("text-anchor", "end");

            report.updateLegend();
            legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y + 18)
                .attr("x", 18)
                .text("RNA type");
                
            var colHeader2 = legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y + 18)
                .attr("x", 110)
                .text("Fraction (%)")
                .attr("fill", "#000");
            legendGroup.append("text")
                .attr("class", "legendCategoryHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y)
                .attr("x", 240)
                .attr("fill", "#000")
                .text("― Reference seqs ―")
                .style("text-anchor", "start");
            legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y + 18)
                .attr("x", 299)
                .attr("fill", "#000")
                .text("Detected")
                .style("text-anchor", "end");
            legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y + 18)
                .attr("x", 373)
                .attr("fill", "#000")
                .text("Total")
                .style("text-anchor", "end");
        },
        updateLegend: function() {
            var report = this;
            var rnaDetectionCounts = report.rnaDetectionCounts();
            var detectedRNATypes = report.detectedRNATypes();
            var rnaTypeFractions = report.rnaTypeFractions();
            report.chart.selectAll(".legend").style("opacity", function(d, i) {
                if (detectedRNATypes[d]) {
                    return "1";
                } else {
                    return "0.3";
                }
            });
            report.chart.selectAll("rect.legendCol2").style("opacity", function(d, i) {
                if (detectedRNATypes[d]) {
                    return "1";
                } else {
                    return "0";
                }
            });
            report.chart.selectAll(".legend .legendCol1").text(function(d) {
                return report.rnaTypeNames[d];
            });
            report.chart.selectAll(".legend .legendCol2").text(function(d) {
                var statsText = "" + d3.format(".1f")(rnaTypeFractions[d]*100) +  "%";
                return statsText;
            });
            report.chart.selectAll(".legend .legendCol3").text(function(d) {
                if (d < 4) {
                    return rnaDetectionCounts[d];
                } else {
                    return "-";
                }
            });
            report.chart.selectAll(".legend .legendCol4").text(function(d) {
                if (d < 4) {
                    return qcReport.rnaTypeRefSeqCounts[d];
                } else {
                    return "-";
                }
            });
        }
    };


    /*************************************************************************
     *                          CONTAMINATION REPORT                         *
     *************************************************************************/

    var contaminationReport = {
        id: "contamination",
        /* Default settings. */
        data: qcReportResults,
        legendYOffset: 65,
        legendHeight: 275,
        get svgHeight() {
            return (this.plotHeight + margins.top + margins.bottom + 
                    this.legendYOffset + this.legendHeight);
        },
        get xScale() { 
            return d3.scale.ordinal()
                .rangeBands([0, PLOT_WIDTH])
                .domain(this.data.map(function(d, i, j) { 
                    return i;
                }))
        },
        /* 
         * Report specific settings. 
         */
        get plotHeight() { 
            return this.target_plot_height;
        },
        get yScale() {
            return d3.scale.linear()
                .domain([0, 1])
                .range([this.plotHeight + margins.top, margins.top]);
        },
        get colorScale() {
            var cladeColors = [
                // Bryophytes, Lycopods, Gymnosperms, Monocots, Dicots

                "#edf8e9",
                "#bae4b3",
                "#74c476",
                "#31a354",
                "#006d2c",

                // Sponges
                "#969696",
                
                // Nematode, Insects, Lophotrochozoa
                "#f6e8c3",
                "#dfc27d",
                "#bf812d",

                // Echinoderms
                "#8c510a",

                // Fish, Birds/Reptiles
                "#eff3ff",
                "#bdd7e7",

                // Rodents, Primates
                "#6baed6",
                "#2171b5"
            ];
            return d3.scale.ordinal()
                .domain([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13])
                .range(cladeColors);
        },
        get cladeNames() {
            var cladeNames = {};
            cladeNames[0] = "Bryophytes";
            cladeNames[1] = "Lycopods";
            cladeNames[2] = "Gymnosperms";
            cladeNames[3] = "Monocots";
            cladeNames[4] = "Dicots";
            cladeNames[5] = "Sponges";
            cladeNames[6] = "Nematode";
            cladeNames[7] = "Insects";
            cladeNames[8] = "Lophotrochozoa";
            cladeNames[9] = "Echinoderms";
            cladeNames[10] = "Fish";
            cladeNames[11] = "Birds/Reptiles";
            cladeNames[12] = "Rodents";
            cladeNames[13] = "Primates";
            return cladeNames;
        },
        get numClades() {
            return 14;
        },
        yAxisLabel: "Clade-specific miRNAs (%)",
        statsKey: "statsClades",
        renderSample: function(report) {
            report.chart.selectAll(".sampleColumnBackground").data(report.data)
              .enter().append("rect")
                .attr("class", "sampleColumnBackground")
                .attr("x", function(d, i, j) { 
                    return i * sample_width + (SAMPLE_PADDING_X) + margins.left;
                })
                .attr("y", margins.top - 1)
                .attr("width", sample_width - (SAMPLE_PADDING_X))
                .attr("height", report.plotHeight + 2)
                .attr("fill", function(d, i, j) {
                    var arr = d["stats"][report.statsKey];
                    return "#000000";
                });
            report.chart.selectAll(".sample").data(report.data)
              .enter().append("text")
                .attr("transform", function(d, i, j) { 
                    var x = i * sample_width + (SAMPLE_PADDING_X) + margins.left + (sample_width / 2);
                    var y = (margins.top - 1) + (report.plotHeight / 2);
                    return "translate(" + x +  ", " + y + ")rotate(-90)";
                })
                .attr("dy", ".31em")
                .style("text-anchor", "middle")
                .attr("class", "noCladedsDetectedWarning")
                .text(function(d) {
                    var arr = d["stats"][report.statsKey];
                    if (d3.sum(arr) === 0) {
                        return "― No clades detected ―";
                    }
                    return "";
                });
            var cell = report.chart.selectAll(".sample").data(report.data)
              .enter().append("g")
                .attr("class", "sample")
                .selectAll("rect")
                .data(function(d, i, j) { 
                    var arr = d["stats"][report.statsKey];
                    if (d3.sum(arr) === 0) {
                        return [];
                    }
                    var cumulativeFracs = [0];
                    var res = [];
                    for (var t=1; t <= arr.length; t++) {
                        var frac = (arr[t-1] / d3.sum(arr));
                        cumulativeFracs[t] = cumulativeFracs[t-1] + frac;
                        res[t-1] = [cumulativeFracs[t-1], cumulativeFracs[t]];
                    }
                    return res;
                })
                .enter();
            cell.append("rect")
                .attr("x", function(d, i, j) { 
                    return j * sample_width + SAMPLE_PADDING_X + margins.left;
                })
                .attr("y", function(d, i, j) {
                    return (report.plotHeight) - (d[1] * report.plotHeight) + margins.top;
                })
                .attr("height", function(d, i, j) {
                    return (d[1] - d[0])*(report.plotHeight);
                })
                .attr("width", sample_width - SAMPLE_PADDING_X)
                .style("fill", function(d, i, j) {
                    return report.colorScale(i);
                });
        },
        cladeDetectionCounts: function() {
            var t = this;
            var activeSelection = anyColIsClicked(clickedStateVector);
            var cladeDetectedStatus = [];
            var cladeDetectionCounts = [];
            t.colorScale.domain().forEach(function(d) {
                cladeDetectedStatus[d] = {};
                cladeDetectionCounts[d] = 0;
                for (var i=0; i < qcReportResults.length; i++) {
                    if (activeSelection && !clickedStateVector[i])
                        continue;
                    for (var j=0; j < t.data[i].stats["foundCladeFamilies"][d].length; j++) {
                        var familyId = t.data[i].stats["foundCladeFamilies"][d][j];
                        cladeDetectedStatus[d][familyId] = true;
                    }
                }
                for (var k in cladeDetectedStatus[d]) {
                    if (cladeDetectedStatus[d].hasOwnProperty(k)) {
                        if (cladeDetectedStatus[d][k]) {
                            cladeDetectionCounts[d]++;
                        }
                    }
                }
            });
            return cladeDetectionCounts;
        },
        cladeFractions: function() {
            var t = this;
            var cladeFractions = [];
            var activeSelection = anyColIsClicked(clickedStateVector);
            var totalReadCount = 0;
            for (var i=0; i < qcReportResults.length; i++) {
                if (activeSelection && !clickedStateVector[i])
                    continue;
                var s = d3.sum(t.data[i].stats.statsClades);
                totalReadCount += s;
            }
            t.colorScale.domain().forEach(function(d) {
                cladeFractions[d] = 0;
                for (var i=0; i < qcReportResults.length; i++) {
                    if (activeSelection && !clickedStateVector[i])
                        continue;
                    cladeFractions[d] += (t.data[i].stats.statsClades[d] / totalReadCount);
                }
            });
            return cladeFractions;
        },
        renderLegend: function(legendGroup, report) {
            var cladeDetectionCounts = report.cladeDetectionCounts();
            var legendEntry = legendGroup.selectAll(".legend")
                .data(report.colorScale.domain())
              .enter().append("g")
                .attr("class", "legend")
                .style("opacity", function(d, i) {
                    if (cladeDetectionCounts[i] == 0) {
                        return "0.3";
                    } else {
                        return "1";
                    }
                })
                .attr("transform", function(d, i) { 
                    var x = 0;
                    var y = (report.numClades - 1 - i)* 20 + 18;
                    return "translate(" + x + "," + y + ")"; 
                });
            legendEntry.append("rect")
                .attr("x", 0)
                .attr("height", 14)
                .attr("width", 14)
                .style("stroke", "#000000")
                .style("stroke-width", 1)
                .style("shape-rendering", "crispEdges")
                .style("fill", report.colorScale);

            legendEntry.append("text")
                .attr("x", 18)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCol1")
                .style("text-anchor", "start");
            legendEntry.append("rect")
                .attr("x", 137)
                .attr("y", -2)
                .attr("rx", 6)
                .attr("ry", 6)
                .attr("class", "legendCol2")
                .attr("width", function(d) { return 56; })
                .attr("height", function(d) { return 18; })
                .style("fill", "#303030");
            legendEntry.append("text")
                .attr("x", 140 + 48)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCol2")
                .style("text-anchor", "end")
                .style("fill", "#fff");
            legendEntry.append("text")
                .attr("x", 299)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCol3")
                .style("text-anchor", "end");
            legendEntry.append("text")
                .attr("x", 371)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .attr("class", "legendCol4")
                .style("text-anchor", "end");
                
            report.updateLegend();
            legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y + 18)
                .attr("x", 18)
                .text("Clade");
                
            var colHeader2 = legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y + 18)
                .attr("x", 140)
                .text("Fraction (%)")
                .attr("fill", "#000");
            legendGroup.append("text")
                .attr("class", "legendCategoryHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y)
                .attr("x", 240)
                .attr("fill", "#000")
                .text("― miRNA families ―")
                .style("text-anchor", "start");
            legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y + 18)
                .attr("x", 299)
                .attr("fill", "#000")
                .text("Detected")
                .style("text-anchor", "end");
            legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y + 18)
                .attr("x", 371)
                .attr("fill", "#000")
                .text("Total")
                .style("text-anchor", "end");

        },
        updateLegend: function() {
            var report = this;
            var cladeDetectionCounts = report.cladeDetectionCounts();
            var cladeFractions = report.cladeFractions();
            report.chart.selectAll(".legend").style("opacity", function(d, i) {
                if (cladeDetectionCounts[i] == 0) {
                    return "0.3";
                } else {
                    return "1";
                }
            });
            report.chart.selectAll("rect.legendCol2").style("opacity", function(d, i) {
                if (cladeDetectionCounts[i] == 0) {
                    return "0";
                } else {
                    return "1";
                }
            });
            report.chart.selectAll(".legend .legendCol1").text(function(d) {
                return report.cladeNames[d];
            });
            report.chart.selectAll(".legend .legendCol2").text(function(d) {
                var statsText = "" + d3.format(".1f")(cladeFractions[d]*100) +  "%";
                return statsText;
            });
            report.chart.selectAll(".legend .legendCol3").text(function(d) {
                return cladeDetectionCounts[d];
            });
            report.chart.selectAll(".legend .legendCol4").text(function(d) {
                return qcReport.cladeRefFamilyCounts[d];
            });
        }
    };

    /*************************************************************************
     *                        MIRNA COMPLEXITY REPORT                        *
     *************************************************************************/

    var complexityReport = {
        id: "complexity",
        /* Default settings. */
        data: qcReportResults,
        legendYOffset: 60,
        legendHeight: 30,
        get svgHeight() {
            return (this.plotHeight + margins.top + margins.bottom + 
                    this.legendYOffset + this.legendHeight);
        },
        get xScale() { 
            return d3.scale.ordinal()
                .rangeBands([0, PLOT_WIDTH])
                .domain(this.data.map(function(d, i, j) { 
                    return i;
                }))
        },
        /* 
         * Report specific settings. 
         */
        statsKey: "statsComplexityReadDepth",
        get numDetectionCountBins() {
            return this.detectionCountBins.length;
        },
        /* Returns a list of (start, end) bins (of the gene counts found). 
         * Note that the [start, end] coords are inclusive. 
         */
        get detectionCountBins() {
            var TARGET_FRACTION_IN_LAST_BIN = 0.3;
            var THRESHOLD_FOR_REQUIRING_BINS_TO_BE_A_FACTOR_OF_TEN = 115;
            var bins = [];
            if (this.maxDetectionCount < 1) {
                bins.push([0, 0]);
            } else if (this.maxDetectionCount <= 8) {
                for (var i=0; i < this.maxDetectionCount; i++) {
                    bins.push([i, i]);
                }
            } else if (this.maxDetectionCount < THRESHOLD_FOR_REQUIRING_BINS_TO_BE_A_FACTOR_OF_TEN){
                var numBins = 9;
                var perBin = Math.floor(this.maxDetectionCount / 9);
                var start = 0;
                for (var i=0; i < numBins - 1; i++) {
                    bins.push([start, start + perBin - 1]);
                    start += perBin;
                }
                bins.push([start, this.maxDetectionCount]);
            } else {
                var numBins = 9;
                var perBin = Math.floor(
                        ((this.maxDetectionCount * (1 - TARGET_FRACTION_IN_LAST_BIN)) / 8) / 10
                ) * 10;
                var start = 0;
                for (var i=0; i < numBins - 1; i++) {
                    bins.push([start, start + perBin - 1]);
                    start += perBin;
                }
                bins.push([start, this.maxDetectionCount]);
            }
            return bins;
        },
        get maxDetectionCount() {
            return qcReport.rnaTypeRefSeqCounts[0];
        },
        get highestCurrentDetectionCount() {
            var activeSelection = anyColIsClicked(clickedStateVector);
            var high = 0;
            for (i=0; i < qcReportResults.length; i++) {
                if (activeSelection && !clickedStateVector[i])
                    continue;
                if (qcReportResults[i].stats.statsComplexityReadDepth.length > high) {
                    high = qcReportResults[i].stats.statsComplexityReadDepth.length;
                }
            }
            high -= 1; /* Array len = 1 means that the detection count is zero. */
            return high;
        },
        get maxDepth() {
            var maxD = 0;
            for (i=0; i < this.data.length; i++) {
                if (this.data[i].stats.allSeqsCount > maxD) {
                    maxD = this.data[i].stats.allSeqsCount;
                }
            }
            return maxD;
        },
        get plotColors() {
            return colorbrewer.Purples[this.numDetectionCountBins].slice();
        },
        get colorScale() {
            var domain = Array.apply(null, Array(this.numDetectionCountBins)).map(function (_, i) {return i;});
            return d3.scale.ordinal()
                .domain(domain)
                .range(this.plotColors);
        },
        get plotHeight() { 
            return this.target_plot_height;
        },
        get yScale() {
            /* yScale for y-axis. */
            return d3.scale.linear()
                .domain([0, this.maxDepth])
                .range([this.plotHeight + margins.top, margins.top]);
        },
        get yDataScale() {
            return d3.scale.linear()
                .domain([0, this.maxDepth])
                .range([this.plotHeight, 0]);
        },
        yAxisLabel: "Read depth",
        renderSample: function(report) {
            report.chart.selectAll(".sampleColumnBackground").data(report.data)
              .enter().append("rect")
                .attr("class", "sampleColumnBackground")
                .attr("x", function(d, i, j) { 
                    return i * sample_width + (SAMPLE_PADDING_X) + margins.left;
                })
                .attr("y", function(d, i, j) {
                    var y = Math.floor(report.yScale(d.stats.allSeqsCount)) - 1;
                    return y;
                })
                .attr("width", sample_width - (SAMPLE_PADDING_X))
                .attr("height", function(d, i, j) {
                    var height = report.plotHeight - Math.floor(report.yDataScale(d.stats.allSeqsCount)) + 2;
                    return height;
                })
                .attr("fill", "#000000");
            var cell = report.chart.selectAll(".sample").data(report.data)
              .enter().append("g")
                .attr("class", "sample")
                .selectAll("rect")
                .data(function(d, i, j) { 
                    /* We want to create an array of (start, end) y-coords
                     * for the "depth boxes" of this sample. The y-coords are 
                     * read counts, i.e. in the interval [0, maxDepth].
                     */
                    var detectionCountBins = report.detectionCountBins;
                    var depths = d.stats.statsComplexityReadDepth;
                    var sampleDetectionCount = depths.length - 1;
                    var boxDepthCoords = [];
                    var binStart = 0;
                    for (var bin=0; bin < detectionCountBins.length; bin++) {
                        var binEnd = detectionCountBins[bin][1];
                        if (binEnd >= sampleDetectionCount) {
                            var boxStart = depths[binStart];
                            var boxEnd = d.stats.allSeqsCount;
                            boxDepthCoords.push([boxStart, boxEnd]);
                            break;
                        } else {
                            var boxStart = depths[binStart];
                            var boxEnd = depths[binEnd];
                            boxDepthCoords.push([boxStart, boxEnd]);
                        }
                        var binStart = binEnd;
                    }
                    return boxDepthCoords;
                })
                .enter();
            cell.append("rect")
                .attr("x", function(d, i, j) { 
                    return j * sample_width + SAMPLE_PADDING_X + margins.left;
                })
                .attr("y", function(d, i, j) {
                    var yres = (Math.floor(report.yScale(d[1])));
                    return yres;
                })
                .attr("height", function(d, i, j) {
                    var height = report.plotHeight - Math.floor(report.yDataScale(d[1] - d[0]));
                    if (height < 0)
                        height = 0;
                    return height;
                })
                .attr("width", sample_width - SAMPLE_PADDING_X)
                .style("fill", function(d, i, j) {
                    return report.plotColors[i];
                });
        },
        legendCaption: "Distinct miRNA gene count intervals",
        renderLegend: function(legendGroup, report) {
            var legendEntry = legendGroup.selectAll(".legend")
                .data(report.colorScale.domain())
              .enter().append("g")
                .attr("class", "legend")
                .attr("transform", function(d, i) { 
                    var x = Math.floor(i / 3) * 110;
                    var y = (i % 3) * 20;
                    return "translate(" + x + "," + y + ")"; 
                });
            legendEntry.append("rect")
                .attr("x", 0)
                .attr("height", 14)
                .attr("width", 14)
                .style("stroke", "#000000")
                .style("stroke-width", 1)
                .style("shape-rendering", "crispEdges")
                .style("fill", report.colorScale);
            legendEntry.append("text")
                .attr("x", 18)
                .attr("y", 7)
                .attr("dy", "0.35em")
                .style("text-anchor", "start")
                .text(function(d, i) { 
                    return report.detectionCountBins[i][0] +  "—" + report.detectionCountBins[i][1];
                });
            legendGroup.append("text")
                .attr("class", "legendTableHeader")
                .attr("y", LEGEND_CAPTION_OFFSET_Y)
                .text(report.legendCaption);
            report.updateLegend();
        },
        updateLegend: function() {
            var report = this;
            report.chart.selectAll(".legend")
                .style("opacity", function(d, i) {
                    if (report.highestCurrentDetectionCount < report.detectionCountBins[i][0]) {
                        return "0.3";
                    } else {
                        return "1";
                    }
                })
        }
    };

    /* Unhide reports. */
    if (qcReport.mirtraceMode === "qc") {
        d3.selectAll(".report").style("display", "block");
    } else if (qcReport.mirtraceMode === "trace") {
        d3.select("#contaminationReport .reportTitle").text("Clade-Specific miRNA Profile");
        d3.selectAll("#header, #contaminationReport").style("display", "block");
    } else {
        alert("Unknown miRTrace mode.");
    }

    var renderReports = function() {
        if (qcReport.mirtraceMode === "qc") {
            renderReport(phredReport, d3.select("#phredReport"));
            renderReport(lengthReport, d3.select("#lengthReport"));
            renderReport(qcStatsReport, d3.select("#qcStatsReport"));
            renderReport(rnaTypeReport, d3.select("#rnaTypeReport"));
            renderReport(complexityReport, d3.select("#complexityReport"));
        }
        renderReport(contaminationReport, d3.select("#contaminationReport"));
    }
    renderReports();
    if (qcReport.mirtraceMode === "qc") {
        renderSampleStatsTable();
    }

    /*************************************************************************/

    /* Setup a callback to check for window size changes every little while. */
    var resizeTimer;
    var resizeTimerWrapperFunction = function() {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(resizeTimerWrapperFunction, 50);
        rerenderSampleColumns();
    };
    resizeTimerWrapperFunction();
})(); 
