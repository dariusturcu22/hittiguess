// Login, register, forgot-password, and the OAuth2 redirect screen mirror
// their own mockups exactly: a single full-viewport background with one
// centered card (or, for the OAuth2 redirect screen, centered content with
// no card), and nothing else. No shared header, no nav, no theme toggle,
// since none of the four mockups have one; each page puts its own logo at
// the top of its own content instead. See
// frontend/app/(marketing)/(landing)/layout.tsx for the landing page's
// separate, header-owning layout.
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <div className="min-h-screen flex flex-col">{children}</div>;
}
