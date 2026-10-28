import { Link } from 'react-router-dom';
import { Empty } from '../components/Feedback';

export function NotFoundPage() {
  return (
    <Empty title="There’s no page here">
      <Link to="/" className="btn-link">
        Go to the home page
      </Link>
    </Empty>
  );
}
