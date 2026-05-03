import { Navigate, createBrowserRouter } from 'react-router-dom';
import { LoginPage } from './features/auth/LoginPage';
import { SignupPage } from './features/auth/SignupPage';
import { TeamSetupPage } from './features/team-setup/TeamSetupPage';
import { PlanningPage } from './features/planning/PlanningPage';
import { ReviewPage } from './features/review/ReviewPage';
import { SprintClosePage } from './features/sprint-close/SprintClosePage';
import { ProtectedRoute } from './components/shared/ProtectedRoute';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/teams" replace />,
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
]);
