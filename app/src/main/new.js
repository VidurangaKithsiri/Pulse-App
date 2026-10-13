    if (user.role === 'admin') {
        return redirect('/admin/dashboard');
    } else if (user.role === 'seller') {
        return redirect('/seller/dashboard');
    }
    