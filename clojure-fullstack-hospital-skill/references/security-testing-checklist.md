# Security And Testing Checklist

## Sensitive data

Check that code does not log:

- Patient names.
- Medical notes.
- Personal identifiers.
- Phone numbers.
- Addresses.
- Tokens.
- Credentials.
- Raw request or response bodies with sensitive fields.

## Authorization

Backend must enforce permissions. Frontend checks are not enough.

Check:

- Route-level auth.
- Domain action permission.
- Tenant, hospital, department, or organization scoping.
- Export and bulk action permissions.
- Audit events for sensitive actions.

## Input validation

Check:

- Backend validates body, query, and path params.
- SQL uses parameters.
- IDs are parsed safely.
- Date intervals are valid.
- Status transitions are valid.
- Frontend validation mirrors backend but does not replace it.

## Backend tests

Cover:

- Domain happy path.
- Domain invalid transitions.
- Permission denied.
- Not found.
- Validation error.
- DB persistence.
- Audit event generation.

## Frontend tests

Cover:

- API adapters.
- Form validation helpers.
- Permission-driven action visibility.
- Empty, loading, and error states.

## Mobile tests

Cover:

- API adapters.
- State handlers.
- Pure formatting and validation.
- Important widget behavior with `:widget` tests.

## Regression tests

Add regression tests for:

- Privacy leaks.
- Permission bypasses.
- Duplicate appointments.
- Race conditions around status changes.
- Incorrect timezone display or filtering.
