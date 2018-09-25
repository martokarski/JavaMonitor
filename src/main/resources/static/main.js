$(document).ready(function () {
    var NUMBER_OF_GC = 20;

    var GB_SIZE = 1024 * 1024 * 1024;
    var MB_SIZE = 1024 * 1024;
    var KB_SIZE = 1024;

    var working = false;
    var lastGC = 0;

    function setStatus(status) {
        working = status.working;
        var $toggle = $("#toggle-profiler");
        $toggle.attr("disabled", false);
        if (working) {
            console.log("Profiler already working");
            $toggle.text("Stop");
            $("#class-table").show();
            updateClasses();
        } else {
            console.log("Profiler not working");
            $toggle.text("Start");
            var $transformed = $("#transformed-classes");
            var $gatherStacktrace = $("#gather-stacktrace");
            $transformed.attr("disabled", false);
            $gatherStacktrace.attr("disabled", false);
        }
    }

    function updateMonitoredClasses(monitoredClasses) {
        var transformedClasses = monitoredClasses.join('\n');
        $("#transformed-classes").val(transformedClasses);
    }

    function renderStacktrace(stacktrace, allOcurrences) {
        if (!allOcurrences) {
            allOcurrences = stacktrace.value;
        }
        var $container = $("<div class='stack-container'>");

        if (stacktrace.name) {
            var $label = $("<div>");
            var $text = $("<p class='stack-element'>");

            var $arrow;
            if (stacktrace.value * 2 > allOcurrences) {
                $arrow = $("<i class='arrow down'>");
            } else {
                $arrow = $("<i class='arrow right'>");
            }
            $arrow.click(function (event) {
                var $this = $(this);
                var classes = $this.attr("class");
                $this.removeClass();
                var $parents = $this.parent().next();
                if (classes.indexOf("down") !== -1) {
                    $this.addClass("arrow right");
                    $parents.css("display", "none");
                } else {
                    $this.addClass("arrow down");
                    $parents.css("display", "block");
                }
            });
            if (stacktrace.children.length === 0) {
                $arrow.css("visibility", "hidden");
            }
            $label.append($arrow);
            $text.text(stacktrace.name + " " + stacktrace.value);
            $label.append($text);
            $container.append($label);
        }

        var $list = $("<ul class='stack-parents'>");
        if (stacktrace.value * 2 > allOcurrences) {
            $list.css("display", "block");
        } else {
            $list.css("display", "none");
        }
        $container.append($list);
        stacktrace.children.forEach(function (stack) {
            var $child = renderStacktrace(stack, allOcurrences);
            var $el = $("<li class='parent-element'>");
            $list.append($el.append($child));
        });

        return $container;
    }

    function removeInitialZeros(tab) {
        var isInitial = true;

        return tab.filter(function (el) {
            if (el > 0) {
                isInitial = false;
            }

            return !isInitial;
        })
    }

    function toggleProfiler() {
        $(this).attr("disabled", true);
        if (working) {
            $.post("/stop", setStatus);
        } else {
            var $transformed = $("#transformed-classes");
            var $gatherStacktrace = $("#gather-stacktrace");
            $transformed.attr("disabled", true);
            $gatherStacktrace.attr("disabled", true);
            var $table = $("#class-table-body");
            $table.empty();
            var transformedClasses = $transformed.val().split('\n');
            var stacktraceEnabled = $gatherStacktrace[0].checked;
            $.post("/start", JSON.stringify({
                includedClasses: transformedClasses,
                gatherStacktrace: stacktraceEnabled
            }), setStatus);
        }
    }

    function invokeGC() {
        $.post("/gc");
    }

    function updateChart(chart, spaceType, gc) {
        var current = gc.gcType === "current";
        var usedData = chart.data.datasets[0].data;
        var committedData = chart.data.datasets[1].data;

        if (usedData.length > 1 && usedData[usedData.length - 1].isCurrent) {
            usedData.splice(-1, 1);
        }
        if (committedData.length > 1 && committedData[committedData.length - 1].isCurrent) {
            committedData.splice(-1, 1);
        }

        if (usedData.length > NUMBER_OF_GC) {
            usedData.splice(0, usedData.length - NUMBER_OF_GC);
        }

        if (committedData.length > NUMBER_OF_GC) {
            committedData.splice(0, committedData.length - NUMBER_OF_GC);
        }

        if (!current) {
            usedData.push({x: gc.startTime, y: gc.memoryBefore[spaceType].used, isCurrent: current, name: gc.gcType});
            committedData.push({
                x: gc.startTime,
                y: gc.memoryBefore[spaceType].committed,
                isCurrent: current,
                name: gc.gcType
            });
        }
        usedData.push({x: gc.endTime, y: gc.memoryAfter[spaceType].used, isCurrent: current, name: gc.gcType});
        committedData.push({
            x: gc.endTime,
            y: gc.memoryAfter[spaceType].committed,
            isCurrent: current,
            name: gc.gcType
        });
    }

    function updateGC() {
        $.get("/getgc", {"lastTime": lastGC}, function (resp) {
            resp.forEach(function (gc) {
                updateChart(edenChart, "eden", gc);
                updateChart(survivorChart, "survivor", gc);
                updateChart(oldChart, "old", gc);
            });
            edenChart.update();
            survivorChart.update();
            oldChart.update();

            if (resp.length > 1) {
                var gc = resp[resp.length - 2];
                lastGC = gc.endTime;
            }
            setTimeout(updateGC, 2 * 1000);
        });
    }

    function updateClasses() {
        if (working) {
            $.get("/trackerupdate", function (resp) {
                if (resp) {
                    var $table = $("#class-table-body");
                    $table.empty();
                    resp.forEach(function (classInfo) {
                        var classNameColumn = $("<td>");
                        var instancesNumber = $("<td>");
                        var generationsNumber = $("<td>");
                        var details = $("<td>");

                        var detailsButton = $("<button>");
                        detailsButton.text("View details");
                        detailsButton.click(function () {
                            var container = $("#age-histogram-container");
                            container.empty();

                            var title = $("#modal-title");
                            title.empty();
                            title.text(classInfo.className);

                            var flameContainer = $("#flame-chart");
                            flameContainer.empty();
                            var flamegraph = d3.flamegraph().width(960);
                            d3
                                .select("#flame-chart")
                                .datum(classInfo.stacktrace)
                                .call(flamegraph);

                            var canvas = $("<canvas>");
                            container.append(canvas);
                            var tab = removeInitialZeros(classInfo.histogram);
                            new Chart(canvas, {
                                type: "bar",
                                data: {
                                    labels: Array.from({length: tab.length}, function (v, k) {
                                        return k + 1;
                                    }),
                                    datasets: [{
                                        label: "Age histogram",
                                        data: tab.reverse()
                                    }]
                                },
                                options: {
                                    scales: {
                                        yAxes: [{
                                            ticks: {
                                                beginAtZero: true
                                            },
                                            scaleLabel: {
                                                display: true,
                                                labelString: "Number of objects"
                                            }
                                        }],
                                        xAxes: [{
                                            scaleLabel: {
                                                display: true,
                                                labelString: "Age of objects"
                                            }
                                        }]
                                    },
                                    maintainAspectRatio: false
                                }
                            });

                            var $stacktraceContainer = $("#all-stacktrace-render");
                            $stacktraceContainer.empty();
                            if (classInfo.stacktrace.value === 0) {
                                $("#accordionExample").css("display", "none");
                            } else {
                                $("#accordionExample").css("display", "block");
                            }
                            $stacktraceContainer.append(renderStacktrace(classInfo.stacktrace));
                        });
                        detailsButton.attr("data-toggle", "modal");
                        detailsButton.attr("data-target", "#exampleModal");
                        detailsButton.addClass("btn");
                        details.append(detailsButton);

                        classNameColumn.text(classInfo.className);
                        instancesNumber.text(classInfo.instancesCount);
                        generationsNumber.text(classInfo.histogram.reduce(function (previousValue, currentValue, index, array) {
                            if (currentValue > 0) {
                                return previousValue + 1;
                            } else {
                                return previousValue;
                            }
                        }, 0));

                        $table.append($("<tr>").append(classNameColumn).append(instancesNumber).append(generationsNumber).append(details));
                    });
                }

                setTimeout(updateClasses, 2 * 1000);
            });
        }
    }

    function printTime(milis) {
        if (milis >= 3600000) {
            return Math.floor(milis / 3600000) + " h " + printTime(milis % 3600000);
        }
        if (milis >= 60000) {
            return Math.floor(milis / 60000) + " m " + printTime(milis % 60000);
        }
        if (milis >= 1000) {
            return Math.floor(milis / 1000) + " s " + printTime(milis % 1000);
        }
        return milis > 0 ? milis + " ms" : "";
    }

    function printMemory(mem) {
        if (mem >= GB_SIZE) {
            return (mem / GB_SIZE).toFixed(3) + " GB";
        }
        if (mem >= MB_SIZE) {
            return (mem / MB_SIZE).toFixed(3) + " MB";
        }
        if (mem >= KB_SIZE) {
            return (mem / KB_SIZE).toFixed(3) + " KB";
        }

        return mem + " B";
    }

    function genericSettings() {
        return {
            type: 'line',
            data: {
                datasets: [
                    {
                        label: 'Used',
                        data: [],
                        backgroundColor: 'rgba(255, 99, 132, 0.2)',
                        borderColor: 'rgba(255,99,132,1)',
                        borderWidth: 1,
                        lineTension: 0
                    },
                    {
                        label: 'Committed',
                        data: [],
                        backgroundColor: 'rgba(255, 99, 132, 0)',
                        borderColor: 'rgba(0,0,0, 0.5)',
                        borderWidth: 1,
                        lineTension: 0
                    }]
            },
            options: {
                scales: {
                    yAxes: [{
                        ticks: {
                            beginAtZero: true,
                            callback: function (value, index, values) {
                                return printMemory(value);
                            }
                        },
                        beforeBuildTicks: function (el) {
                        }
                    }],
                    xAxes: [{
                        type: 'linear',
                        position: 'bottom',
                        ticks: {
                            // Include a dollar sign in the ticks
                            callback: function (value, index, values) {
                                return printTime(value);
                            }
                        }
                    }]
                },
                tooltips: {
                    callbacks: {
                        title: function (items, data) {
                            var datasetIndex = items[0].datasetIndex;
                            var index = items[0].index;
                            var gcName = data.datasets[datasetIndex].data[index].name;
                            return gcName + " " + printTime(items[0].xLabel);
                        },
                        label: function (item, data) {
                            var datasetIndex = item.datasetIndex;
                            var datasetLabel = data.datasets[datasetIndex].label;
                            return datasetLabel + ": " + printMemory(item.yLabel);
                        }
                    }
                },
                maintainAspectRatio: false
            }
        }
    }

    $("#toggle-profiler").click(toggleProfiler);
    $("#invoke-gc").click(invokeGC);

    var edenCanvas = $("#eden-space");
    var edenChart = new Chart(edenCanvas, genericSettings());

    var survivorCanvas = $("#survivor-space");
    var survivorChart = new Chart(survivorCanvas, genericSettings());

    var oldCanvas = $("#old-space");
    var oldChart = new Chart(oldCanvas, genericSettings());

    $.get("/monitoredclasses", updateMonitoredClasses);
    $.get("/status", setStatus);
    updateGC();
    updateClasses();
});