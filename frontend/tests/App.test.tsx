import { MemoryRouter } from 'react-router-dom';
import { render, screen } from '@testing-library/react';

import Landing from '@/pages/landing/Landing';

describe('App', () => {
  it('renders the landing page heading', () => {
    render(
      <MemoryRouter>
        <Landing />
      </MemoryRouter>,
    );
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Ongard Gamemaster');
  });
});
