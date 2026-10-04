# API standards (applies to all future modules)

- Base path: `/api/v1`. Lowercase plural resources (`/api/v1/equipment`, `/api/v1/bookings`).
- DTOs only on the wire (records + Jakarta validation). Never expose JPA entities.
- Errors use `ApiError { timestamp, status, error, message, path }` via `GlobalExceptionHandler`.
- Status codes: `201` create, `200/204` update/delete, `400` validation, `401/403` auth, `404` missing, `409` booking conflict.
- Pagination: `page`, `size`, `sort` query params; responses `{ content, page, size, totalElements, totalPages }`.
- Auth: `Authorization: Bearer <jwt>`; roles enforced with `@PreAuthorize("hasRole('...')")`.
- Docs: every controller needs `@Tag` + `@Operation`; Swagger UI is the contract.
- Time: ISO-8601 UTC (`TIMESTAMPTZ` in Oracle).
