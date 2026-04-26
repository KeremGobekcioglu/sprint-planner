import { Navigate, createBrowserRouter } from 'react-router-dom';
import { TeamSetupPage } from './features/team-setup/TeamSetupPage';
import { PlanningPage } from './features/planning/PlanningPage';
import { ReviewPage } from './features/review/ReviewPage';
import { SprintClosePage } from './features/sprint-close/SprintClosePage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/teams" replace />,
  },
  {
    path: '/teams',
    element: <TeamSetupPage />,
  },
  {
    path: '/teams/:teamId/plan',
    element: <PlanningPage />,
  },
  {
    path: '/sprints/:sprintId/review',
    element: <ReviewPage />,
  },
  {
    path: '/sprints/:sprintId/close',
    element: <SprintClosePage />,
  },
]);
