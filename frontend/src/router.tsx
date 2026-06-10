import { Navigate, createBrowserRouter } from 'react-router-dom';
import { LoginPage } from './features/auth/LoginPage';
import { SignupPage } from './features/auth/SignupPage';
import { TrelloCallbackPage } from './features/auth/TrelloCallbackPage';
import { TeamSetupPage } from './features/team-setup/TeamSetupPage';
import { PlanningPage } from './features/planning/PlanningPage';
import { ReviewPage } from './features/review/ReviewPage';
import { SprintClosePage } from './features/sprint-close/SprintClosePage';
import { RetrospectivePage } from './features/sprint-close/RetrospectivePage';
import { ProfilePage } from './features/profile/ProfilePage';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { OnboardingPage } from './features/onboarding/OnboardingPage';
import { FaqPage } from './features/faq/FaqPage';
import { ProtectedRoute } from './components/shared/ProtectedRoute';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/dashboard" replace />,
  },
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/signup',
    element: <SignupPage />,
  },
  {
    path: '/trello/callback',
    element: <TrelloCallbackPage />,
  },
  {
    path: '/dashboard',
    element: (
      <ProtectedRoute>
        <DashboardPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/teams',
    element: (
      <ProtectedRoute>
        <TeamSetupPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/teams/:teamId/plan',
    element: (
      <ProtectedRoute>
        <PlanningPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/sprints/:sprintId/review',
    element: (
      <ProtectedRoute>
        <ReviewPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/sprints/:sprintId/close',
    element: (
      <ProtectedRoute>
        <SprintClosePage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/sprints/:sprintId/retrospective',
    element: (
      <ProtectedRoute>
        <RetrospectivePage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/profile',
    element: (
      <ProtectedRoute>
        <ProfilePage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/onboarding',
    element: (
      <ProtectedRoute>
        <OnboardingPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/faq',
    element: <FaqPage />,
  },
]);
