package com.doe.manager.api.controller;

import com.doe.manager.api.service.JobService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// current log system sucks under heavy load (log limited at 1mb )
// TODO: implement a better log system
// FIFO approach to keep the logs size constant and dont lose new logs {@ManagerServer} and {@WorkerNode} need to be edited as well

@RestController
@RequestMapping("/api/v1/logs/jobs")
public class JobLogController {

    private final JobService jobService;

    public JobLogController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getJobLogs(
            @PathVariable("id") UUID id,
            @RequestParam(value = "start", required = false) Integer start,
            @RequestParam(value = "length", required = false) Integer length) {
        List<String> logLines = jobService.getJobLogs(id, start, length);
        String formattedLogs = String.join("\n", logLines);

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Job Logs - %s</title>
                    <style>
                        body {
                            background-color: #0f172a;
                            color: #e2e8f0;
                            font-family: 'Fira Code', 'Courier New', Courier, monospace;
                            padding: 20px;
                            margin: 0;
                            line-height: 1.5;
                        }
                        .container {
                            max-width: 1200px;
                            margin: 0 auto;
                        }
                        h1 {
                            color: #38bdf8;
                            font-size: 1.5rem;
                            margin-bottom: 20px;
                            border-bottom: 1px solid #1e293b;
                            padding-bottom: 10px;
                        }
                        pre {
                            background-color: #1e293b;
                            padding: 15px;
                            border-radius: 8px;
                            overflow-x: auto;
                            white-space: pre-wrap;
                            word-wrap: break-word;
                            border: 1px solid #334155;
                            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
                        }
                        .status-bar {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            margin-bottom: 10px;
                            font-size: 0.875rem;
                            color: #94a3b8;
                        }
                        .refresh-indicator {
                            display: inline-block;
                            width: 8px;
                            height: 8px;
                            background-color: #10b981;
                            border-radius: 50%%;
                            margin-right: 5px;
                            animation: pulse 2s infinite;
                        }
                        @keyframes pulse {
                            0%% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
                            70%% { transform: scale(1); box-shadow: 0 0 0 5px rgba(16, 185, 129, 0); }
                            100%% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>Job Logs: %s</h1>
                        <div class="status-bar">
                            <span>Auto-refreshing every 2s</span>
                            <span><span class="refresh-indicator"></span>Live</span>
                        </div>
                        <pre>%s</pre>
                    </div>
                    <script>
                        setTimeout(() => {
                            window.location.reload();
                        }, 2000);
                        window.scrollTo(0, document.body.scrollHeight);
                    </script>
                </body>
                </html>
                """.formatted(id, id, formattedLogs);
        return ResponseEntity.ok(html);
    }
    
    @GetMapping(value = "/{id}/raw", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getJobLogsRaw(
            @PathVariable("id") UUID id,
            @RequestParam(value = "start", required = false) Integer start,
            @RequestParam(value = "length", required = false) Integer length) {
        List<String> logLines = jobService.getJobLogs(id, start, length);
        return ResponseEntity.ok(String.join("\n", logLines));
    }
}
