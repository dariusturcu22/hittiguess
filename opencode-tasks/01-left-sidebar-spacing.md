# 01. Left rail icons are too close together

Branch: `fix/left-rail-spacing`. Frontend only. File: `frontend/components/app-sidebar.tsx`.

## What is wrong

The rail spaces its icons with height steps (custom variants defined in
`frontend/app/globals.css`: `short` = viewport height 860px or less, `very-short` = 700px or less):

```ts
const RAIL_DIVIDER_CLASSES = "w-8 h-0.5 my-3.5 short:my-2.5 very-short:my-1.5 shrink-0 rounded-full bg-sidebar-border";
const RAIL_GROUP_GAP_CLASSES = "gap-5 short:gap-3.5 very-short:gap-2.5";
```

A laptop browser window is usually under 860px tall, so users nearly always get the tighter steps,
and the owner says the icons look squished. The original spacing (before story 53) was too loose;
the target is in between.

## What to build

- Raise each step by one notch: `RAIL_GROUP_GAP_CLASSES = "gap-6 short:gap-5 very-short:gap-3.5"` and
  divider margins `my-4 short:my-3 very-short:my-2`. Leave the admin group (`ADMIN_RAIL_*`) as it is.
- The rail must still fit without its own scrollbar at 1366x768 (about 650px of viewport) for an
  admin account, which has the most icons. If it does not fit, reduce only the `very-short` values.

## Tests

- Update `frontend/components/app-sidebar.test.tsx` if it asserts the old class strings.
- Manual check in the browser at 1366x768, 1440x900, and 1920x1080: no rail scrollbar, even spacing.
