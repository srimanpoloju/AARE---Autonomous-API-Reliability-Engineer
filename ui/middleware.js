import { NextResponse } from 'next/server';

export function middleware(request) {
  const token = request.cookies.get('jwt_token')?.value || request.headers.get('Authorization')?.split(' ')[1]; // Check cookie or Authorization header

  const { pathname } = request.nextUrl;

  // Allow requests to /login and API authentication endpoints
  if (pathname.startsWith('/login') || pathname.startsWith('/api/auth')) {
    return NextResponse.next();
  }

  // Redirect to login if no token and trying to access a protected route
  if (!token && !pathname.startsWith('/login')) {
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('redirect', pathname);
    return NextResponse.redirect(loginUrl);
  }

  // Continue to the requested page if authenticated
  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'], // Apply middleware to all routes except static files
};