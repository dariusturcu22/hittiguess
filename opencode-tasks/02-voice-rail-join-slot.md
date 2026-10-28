# 02. The voice rail's Join button sits at the bottom instead of being the next slot

Branch: `fix/voice-rail-join-slot`. Frontend only. Files: `frontend/components/app-voice-sidebar.tsx`,
`frontend/components/voice-member-list.tsx`, and their `.test.tsx` files.

## What is wrong

When the user is not in the call, `AppVoiceSidebar` renders (around line 132):

```tsx
<aside ...>{memberList}<div className="flex-1" />{...messages}<div ...>{joinButton}</div></aside>
```

The `flex-1` spacer pushes the Join button to the bottom of the rail. The owner has asked several
times for it to behave like the next participant: it sits directly under the last person in the call,
and at the very top when nobody is in the call.

This was already decided: docs/DECISIONS.md, "2026-10-09: Gameplay feedback and voice presentation",
says the rail appears "with join at the top outside a call". The current code breaks that decision.

## What to build

- Not in the call: render the Join control as one more slot of the member list, directly after the
  last member (or first, when the list is empty). Give it the same slot size as a member
  (`VoiceMemberList` uses `h-[86px]` slots with a 46px circle and a 10px name label): a 46px dashed
  circle with the phone icon, and the label "Join" under it. Remove the spacer before it. Error
  messages (microphone, share) go directly under the Join slot.
- In the call: unchanged. The member list is on top, and the controls (mute, deafen, settings, leave)
  stay at the bottom.
- The simplest way: add an optional `trailingSlot?: ReactNode` prop to `VoiceMemberList` and render
  it after the members inside the same scrolling column, so it scrolls with the list when there are
  many members.

## Tests

- `app-voice-sidebar.test.tsx`: with nobody in the call, the Join button is the first child of the
  member column; with two members in the call and the user outside, it comes right after them.
- `voice-member-list.test.tsx`: the trailing slot renders after the members.
