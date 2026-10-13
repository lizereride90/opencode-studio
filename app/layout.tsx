import type { Metadata, Viewport } from 'next';
import './styles.css';

export const metadata: Metadata = {
  title: 'OpenCode Studio',
  description: 'A native-inspired coding workspace for OpenCode on Debian.',
};
export const viewport: Viewport = { themeColor: '#101013' };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
