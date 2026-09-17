import {
  AdminReviewItemDTOPriorityTier,
  type AdminReviewItemDTOPriorityTier as PriorityTier,
} from "@/hooks/models/adminReviewItemDTOPriorityTier";
import {
  AdminReviewItemDTOVerificationStatus,
  type AdminReviewItemDTOVerificationStatus as VerificationStatus,
} from "@/hooks/models/adminReviewItemDTOVerificationStatus";
import { ResolveReportRequestVerificationStatus } from "@/hooks/models/resolveReportRequestVerificationStatus";

export interface TierPresentation {
  label: string;
  chipClassName: string;
}

// The mockup colours each tier chip from raw Catppuccin hex (red/peach/blue/grey).
// The frontend exposes only semantic, theme-flipping tokens, so each tier maps to
// the closest semantic role: destructive (red) for the most urgent tier, primary
// (peach/orange) for reported, accent (mauve) for confirmed since no blue token
// exists, and muted for the lowest tier.
const PRIORITY_TIER_PRESENTATION: Record<PriorityTier, TierPresentation> = {
  [AdminReviewItemDTOPriorityTier.CONVERGING_REPORTS]: {
    label: "CONVERGING",
    chipClassName: "bg-destructive/20 text-destructive",
  },
  [AdminReviewItemDTOPriorityTier.REPORTED_NO_CONVERGENCE]: {
    label: "REPORTED",
    chipClassName: "bg-primary/20 text-primary",
  },
  [AdminReviewItemDTOPriorityTier.CONFIRMED_UNREPORTED]: {
    label: "CONFIRMED",
    chipClassName: "bg-accent/20 text-accent",
  },
  [AdminReviewItemDTOPriorityTier.UNCONFIRMED_UNREPORTED]: {
    label: "UNCONFIRMED",
    chipClassName: "bg-muted text-muted-foreground",
  },
};

const UNKNOWN_TIER_PRESENTATION: TierPresentation = {
  label: "UNKNOWN",
  chipClassName: "bg-muted text-muted-foreground",
};

export function presentPriorityTier(
  tier: PriorityTier | undefined,
): TierPresentation {
  if (!tier) {
    return UNKNOWN_TIER_PRESENTATION;
  }
  return PRIORITY_TIER_PRESENTATION[tier] ?? UNKNOWN_TIER_PRESENTATION;
}

export const VERIFICATION_STATUS_OPTIONS: VerificationStatus[] = [
  AdminReviewItemDTOVerificationStatus.VERIFIED,
  AdminReviewItemDTOVerificationStatus.NEEDS_REVIEW,
  AdminReviewItemDTOVerificationStatus.MANUAL_ENTRY,
  AdminReviewItemDTOVerificationStatus.UNVERIFIED,
];

const RESOLVE_STATUS_BY_REVIEW_STATUS: Record<
  VerificationStatus,
  ResolveReportRequestVerificationStatus
> = {
  [AdminReviewItemDTOVerificationStatus.UNVERIFIED]:
    ResolveReportRequestVerificationStatus.UNVERIFIED,
  [AdminReviewItemDTOVerificationStatus.VERIFIED]:
    ResolveReportRequestVerificationStatus.VERIFIED,
  [AdminReviewItemDTOVerificationStatus.NEEDS_REVIEW]:
    ResolveReportRequestVerificationStatus.NEEDS_REVIEW,
  [AdminReviewItemDTOVerificationStatus.MANUAL_ENTRY]:
    ResolveReportRequestVerificationStatus.MANUAL_ENTRY,
};

export function toResolveVerificationStatus(
  status: VerificationStatus,
): ResolveReportRequestVerificationStatus {
  return RESOLVE_STATUS_BY_REVIEW_STATUS[status];
}
