# Bachat Gat Project Memory

Use this prompt for future work on this project:

> Work on the Bachat Gat / SHG Management Platform in this repository.
>
> Preserve existing functionality and data. Make only the changes explicitly requested. Do not redesign unrelated pages, rename existing fields, remove APIs, or modify backend business logic unless the request requires it. Before editing, inspect the relevant HTML, JavaScript, CSS, controller, service, and configuration files. After editing, verify the affected files and run an appropriate build or test.
>
> The application uses Spring Boot, Java, static HTML/CSS/JavaScript pages, Spring Security/JWT, and Firestore. Static frontend files are under `src/main/resources/static`. Keep the existing visual style, language support, role-based access, group-scoped authorization, and responsive behavior.
>
> For UI requests, change only the named page and components. Keep mobile layouts usable. Prefer targeted edits over broad CSS or JavaScript rewrites. Do not remove buttons, columns, fields, or functionality unless explicitly requested.
>
> Never commit passwords, API keys, SMTP credentials, WhatsApp tokens, JWT secrets, or other private values. Use environment variables or deployment secrets and explain any required configuration clearly.
>
> When an error is reported, identify the root cause from the stack trace or screenshot, fix the cause, and explain what was changed in simple language. If an external service such as Gmail SMTP or WhatsApp Cloud API requires credentials, do not invent credentials; provide the exact environment variables and restart instructions.

## Current project-specific notes

- Report email delivery requires `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `REPORT_EMAIL_FROM`.
- WhatsApp report delivery requires `WHATSAPP_REPORTS_ENABLED=true`, `WHATSAPP_PHONE_NUMBER_ID`, and `WHATSAPP_ACCESS_TOKEN`.
- Missing optional static assets should be handled as normal 404 responses, not logged as internal server errors.
- When modifying tables, preserve the existing data and behavior, and make mobile scrolling/responsiveness explicit where needed.
